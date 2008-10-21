/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import com.android.mms.R;
import com.android.mms.transaction.Transaction;
import com.android.mms.transaction.TransactionBundle;
import com.android.mms.transaction.TransactionService;
import com.android.mms.util.DownloadManager;
import com.google.android.mms.pdu.PduHeaders;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * This class provides view of a message in the messages list.
 */
public class MessageListItem extends LinearLayout implements
        SlideViewInterface, OnClickListener {
    public static final String EXTRA_URLS = "com.android.mms.ExtraUrls";

    private static final String TAG = "MessageListItem";
    private static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);

    static final int MSG_LIST_EDIT_MMS   = 1;
    static final int MSG_LIST_EDIT_SMS   = 2;

    private View mMsgListItem;
    private View mMmsView;
    private ImageView mImageView;
    private ImageView mLeftStatusIndicator;
    private ImageView mRightStatusIndicator;
    private ImageButton mSlideShowButton;
    private TextView mBodyTextView;
    private Button mDownloadButton;
    private TextView mDownloadingLabel;
    private Handler mHandler;

    public MessageListItem(Context context) {
        super(context);
    }

    public MessageListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mMsgListItem = findViewById(R.id.msg_list_item);
        mLeftStatusIndicator = (ImageView) findViewById(R.id.left_status_indicator);
        mBodyTextView = (TextView) findViewById(R.id.text_view);
        mRightStatusIndicator = (ImageView) findViewById(R.id.right_status_indicator);
        mDownloadButton = (Button) findViewById(R.id.btn_download_msg);
        mDownloadingLabel = (TextView) findViewById(R.id.label_downloading);
    }

    public void bind(MessageItem msgItem) {
        setLongClickable(false);

        switch (msgItem.mMessageType) {
            case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                bindNotifInd(msgItem);
                break;
            default:
                bindCommonMessage(msgItem);
                break;
        }
    }

    public void setMsgListItemHandler(Handler handler) {
        mHandler = handler;
    }

    private void bindNotifInd(final MessageItem msgItem) {
        hideMmsViewIfNeeded();

        String msgSizeText = mContext.getString(R.string.message_size_label)
                                + String.valueOf((msgItem.mMessageSize + 1023) / 1024)
                                + mContext.getString(R.string.kilobyte);

        mBodyTextView.setText(formatMessage(msgItem.mContact, null, msgItem.mSubject,
                                            msgSizeText + "\n" + msgItem.mTimestamp));

        int state = DownloadManager.getInstance().getState(msgItem.mMessageUri);
        switch (state) {
            case DownloadManager.STATE_DOWNLOADING:
                mDownloadingLabel.setVisibility(View.VISIBLE);
                mDownloadButton.setVisibility(View.GONE);
                break;
            case DownloadManager.STATE_UNSTARTED:
            case DownloadManager.STATE_TRANSIENT_FAILURE:
            case DownloadManager.STATE_PERMANENT_FAILURE:
            default:
                setLongClickable(true);
                mDownloadingLabel.setVisibility(View.GONE);
                mDownloadButton.setVisibility(View.VISIBLE);
                mDownloadButton.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        mDownloadingLabel.setVisibility(View.VISIBLE);
                        mDownloadButton.setVisibility(View.GONE);
                        Intent intent = new Intent(mContext, TransactionService.class);
                        intent.putExtra(TransactionBundle.URI, msgItem.mMessageUri.toString());
                        intent.putExtra(TransactionBundle.TRANSACTION_TYPE,
                                Transaction.RETRIEVE_TRANSACTION);
                        mContext.startService(intent);
                    }
                });
                break;
        }

        // Hide the error indicator.
        mRightStatusIndicator.setVisibility(View.GONE);

        drawLeftStatusIndicator(msgItem.mBoxId);
    }

    private void bindCommonMessage(final MessageItem msgItem) {
        mDownloadButton.setVisibility(View.GONE);
        mDownloadingLabel.setVisibility(View.GONE);

        // Since the message text should be concatenated with the sender's
        // address(or name), I have to display it here instead of
        // displaying it by the Presenter.
        mBodyTextView.setTransformationMethod(HideReturnsTransformationMethod.getInstance());

        mBodyTextView.setText(formatMessage(msgItem.mContact, msgItem.mBody,
                                            msgItem.mSubject, msgItem.mTimestamp));
        // TODO part of changing contact names to links
        //mBodyTextView.setText(formatMessage(msgItem.mAddress, msgItem.mBody));

        if (msgItem.isSms()) {
            hideMmsViewIfNeeded();
        } else {
            Presenter presenter = PresenterFactory.getPresenter(
                    "MmsThumbnailPresenter", mContext,
                    this, msgItem.mSlideshow);
            presenter.present();

            if (msgItem.mAttachmentType != AttachmentEditor.TEXT_ONLY) {
                inflateMmsView();
                mMmsView.setVisibility(View.VISIBLE);
                setOnClickListener(msgItem);
                drawPlaybackButton(msgItem);
            } else {
                hideMmsViewIfNeeded();
            }
        }

        drawLeftStatusIndicator(msgItem.mBoxId);
        drawRightStatusIndicator(msgItem);
    }

    private void hideMmsViewIfNeeded() {
        if (mMmsView != null) {
            mMmsView.setVisibility(View.GONE);
        }
    }

    public void startAudio() {
        // TODO Auto-generated method stub
    }

    public void startVideo() {
        // TODO Auto-generated method stub
    }

    public void setAudio(Uri audio, String name, Map<String, ?> extras) {
        // TODO Auto-generated method stub
    }

    public void setImage(String name, Bitmap bitmap) {
        inflateMmsView();

        mImageView.setImageBitmap(bitmap);
        mImageView.setVisibility(VISIBLE);
    }

    private void inflateMmsView() {
        if (mMmsView == null) {
            //inflate the surrounding view_stub
            findViewById(R.id.mms_layout_view_stub).setVisibility(VISIBLE);

            mMmsView = findViewById(R.id.mms_view);
            mImageView = (ImageView) findViewById(R.id.image_view);
            mSlideShowButton = (ImageButton) findViewById(R.id.play_slideshow_button);
        }
    }

    private CharSequence formatMessage(String contact, String body, String subject,
                                       String timestamp) {
        SpannableStringBuilder buf = new SpannableStringBuilder(contact);
        buf.append(": ");
        buf.setSpan(STYLE_BOLD, 0, buf.length(),
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

        boolean hasSubject = !TextUtils.isEmpty(subject);
        if (hasSubject) {
            buf.append(mContext.getResources().getString(R.string.inline_subject, subject));
        }

        if (!TextUtils.isEmpty(body)) {
            if (hasSubject) {
                buf.append(" - ");
            }
            buf.append(body);
        }

        buf.append("\n");
        buf.append(timestamp);

        return buf;
    }

    private void drawPlaybackButton(MessageItem msgItem) {
        switch (msgItem.mAttachmentType) {
            case AttachmentEditor.SLIDESHOW_ATTACHMENT:
            case AttachmentEditor.AUDIO_ATTACHMENT:
            case AttachmentEditor.VIDEO_ATTACHMENT:
                // Show the 'Play' button and bind message info on it.
                mSlideShowButton.setTag(msgItem);
                // Set call-back for the 'Play' button.
                mSlideShowButton.setOnClickListener(this);
                mSlideShowButton.setVisibility(View.VISIBLE);
                setLongClickable(true);
                break;
            default:
                mSlideShowButton.setVisibility(View.GONE);
                break;
        }
    }

    // OnClick Listener for the playback button
    public void onClick(View v) {
        MessageItem mi = (MessageItem) v.getTag();
        switch (mi.mAttachmentType) {
            case AttachmentEditor.AUDIO_ATTACHMENT:
            case AttachmentEditor.VIDEO_ATTACHMENT:
            case AttachmentEditor.SLIDESHOW_ATTACHMENT:
                Intent intent = new Intent(
                        mContext, SlideshowActivity.class);
                intent.setData(mi.mMessageUri);
                mContext.startActivity(intent);
                break;
        }
    }

    public void onMessageListItemClick() {
        URLSpan[] spans = mBodyTextView.getUrls();

        if (spans.length == 0) {
            // Do nothing.
        } else if (spans.length == 1) {
            Uri uri = Uri.parse(spans[0].getURL());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);

            mContext.startActivity(intent);
        } else {
            Intent intent = new Intent(
                    mContext, UrlListActivity.class);

            intent.putStringArrayListExtra(
                    EXTRA_URLS, extractUris(spans));
            mContext.startActivity(intent);
        }
    }


    private void setOnClickListener(final MessageItem msgItem) {
        if (msgItem.mAttachmentType == AttachmentEditor.IMAGE_ATTACHMENT) {
            mImageView.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    // FIXME: Use SlideshowActivity to view image for the time being.
                    // As described in UI spec, pressing an inline attachment will
                    // open up the full view of the attachment in its associated app
                    // (here should the pictures app).
                    // But the <ViewImage> would only show images in MediaStore.
                    // Should we save a copy to MediaStore temporarily for displaying?
                    Intent intent = new Intent(mContext, SlideshowActivity.class);
                    intent.setData(msgItem.mMessageUri);
                    mContext.startActivity(intent);
                }
            });
        } else {
            mImageView.setOnClickListener(null);
        }
    }

    private ArrayList<String> extractUris(URLSpan[] spans) {
        int size = spans.length;
        ArrayList<String> accumulator = new ArrayList<String>();

        for (int i = 0; i < size; i++) {
            accumulator.add(spans[i].getURL());
        }
        return accumulator;
    }

    private void drawLeftStatusIndicator(int msgBoxId) {
        switch (msgBoxId) {
            case Mms.MESSAGE_BOX_INBOX:
                mLeftStatusIndicator.setVisibility(VISIBLE);
                mLeftStatusIndicator.setImageResource(R.drawable.textfield_im_left_indicator_blue);
                mMsgListItem.setBackgroundResource(R.drawable.light_blue_background);
                break;

            case Mms.MESSAGE_BOX_DRAFTS:
            case Sms.MESSAGE_TYPE_FAILED:
            case Sms.MESSAGE_TYPE_QUEUED:
            case Mms.MESSAGE_BOX_OUTBOX:
                mLeftStatusIndicator.setVisibility(VISIBLE);
                mLeftStatusIndicator.setImageResource(R.drawable.textfield_im_left_indicator_red);
                mMsgListItem.setBackgroundResource(R.drawable.white_background);
                break;

            default:
                // reserve space for one indicator to preserve alignment
                mLeftStatusIndicator.setVisibility(INVISIBLE);
                mMsgListItem.setBackgroundResource(R.drawable.white_background);
                break;
        }
    }

    private boolean isFailedMessage(MessageItem msgItem) {
        boolean isFailedMms = msgItem.isMms()
                            && (msgItem.mErrorType >= MmsSms.ERR_TYPE_GENERIC_PERMANENT);
        boolean isFailedSms = msgItem.isSms()
                            && (msgItem.mBoxId == Sms.MESSAGE_TYPE_FAILED);
        return isFailedMms || isFailedSms;
    }

    private void setErrorIndicatorClickListener(final MessageItem msgItem) {
        String type = msgItem.mType;
        final int what;
        if (type.equals("sms")) {
            what = MSG_LIST_EDIT_SMS;
        } else {
            what = MSG_LIST_EDIT_MMS;
        }
        mRightStatusIndicator.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (null != mHandler) {
                    Message msg = Message.obtain(mHandler, what);
                    msg.obj = new Long(msgItem.mMsgId);
                    msg.sendToTarget();
                }
            }
        });
    }

    private void drawRightStatusIndicator(MessageItem msgItem) {
        if (msgItem.isOutgoingMessage()) {
            if (isFailedMessage(msgItem)) {
                mRightStatusIndicator.setImageResource(R.drawable.ic_sms_error);
                setErrorIndicatorClickListener(msgItem);
            } else {
                mRightStatusIndicator.setImageResource(R.drawable.ic_email_pending);
            }
            mRightStatusIndicator.setVisibility(View.VISIBLE);
        } else if (msgItem.mDeliveryReport || msgItem.mReadReport) {
            mRightStatusIndicator.setImageResource(R.drawable.ic_mms_message_details);
            mRightStatusIndicator.setVisibility(View.VISIBLE);
        } else {
            mRightStatusIndicator.setVisibility(View.GONE);
        }
    }

    public void setImageRegionFit(String fit) {
        // TODO Auto-generated method stub
    }

    public void setImageVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void setText(String name, String text) {
        // TODO Auto-generated method stub
    }

    public void setTextVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void setVideo(String name, Uri video) {
        inflateMmsView();

        MediaPlayer mp = new MediaPlayer();

        try {
            mp.setDataSource(mContext, video);
            mImageView.setImageBitmap(mp.getFrameAt(1000));
            mImageView.setVisibility(VISIBLE);
        } catch (IOException e) {
            Log.e(TAG, "Unexpected IOException.", e);
        } finally {
            mp.release();
        }
    }

    public void setVideoVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void stopAudio() {
        // TODO Auto-generated method stub
    }

    public void stopVideo() {
        // TODO Auto-generated method stub
    }

    public void reset() {
        if (mImageView != null) {
            mImageView.setVisibility(GONE);
        }
    }

    public void setVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void pauseAudio() {
        // TODO Auto-generated method stub

    }

    public void pauseVideo() {
        // TODO Auto-generated method stub

    }

    public void seekAudio(int seekTo) {
        // TODO Auto-generated method stub

    }

    public void seekVideo(int seekTo) {
        // TODO Auto-generated method stub

    }
}
