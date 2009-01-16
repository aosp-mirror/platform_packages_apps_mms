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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.Map;

import com.google.android.util.SmileyParser;
import com.google.android.util.SmileyResources;

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
    private ImageView mRightStatusIndicator;
    private ImageButton mSlideShowButton;
    private TextView mBodyTextView;
    private Button mDownloadButton;
    private TextView mDownloadingLabel;
    private Handler mHandler;
    private MessageItem mMessageItem;

    // NOTE: if you change anything about this array, you must make the corresponding change
    // to the string arrays: default_smiley_texts and default_smiley_names in res/values/arrays.xml
    public static final int[] DEFAULT_SMILEY_RES_IDS = {
        R.drawable.emo_im_happy,                //  0
        R.drawable.emo_im_sad,                  //  1
        R.drawable.emo_im_winking,              //  2
        R.drawable.emo_im_tongue_sticking_out,  //  3
        R.drawable.emo_im_surprised,            //  4
        R.drawable.emo_im_kissing,              //  5
        R.drawable.emo_im_yelling,              //  6
        R.drawable.emo_im_cool,                 //  7
        R.drawable.emo_im_money_mouth,          //  8
        R.drawable.emo_im_foot_in_mouth,        //  9
        R.drawable.emo_im_embarrased,           //  10
        R.drawable.emo_im_angel,                //  11
        R.drawable.emo_im_undecided,            //  12
        R.drawable.emo_im_crying,               //  13
        R.drawable.emo_im_lips_are_sealed,      //  14
        R.drawable.emo_im_laughing,             //  15
        R.drawable.emo_im_wtf                   //  16
    };
    
    public static final int DEFAULT_SMILEY_TEXTS = R.array.default_smiley_texts;
    public static final int DEFAULT_SMILEY_NAMES = R.array.default_smiley_names;
    
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
        mBodyTextView = (TextView) findViewById(R.id.text_view);
        mRightStatusIndicator = (ImageView) findViewById(R.id.right_status_indicator);
    }

    public void bind(MessageItem msgItem) {
        mMessageItem = msgItem;

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

    public MessageItem getMessageItem() {
        return mMessageItem;
    }
    
    public void setMsgListItemHandler(Handler handler) {
        mHandler = handler;
    }

    private void bindNotifInd(final MessageItem msgItem) {
        hideMmsViewIfNeeded();

        String msgSizeText = mContext.getString(R.string.message_size_label)
                                + String.valueOf((msgItem.mMessageSize + 1023) / 1024)
                                + mContext.getString(R.string.kilobyte);
        
        boolean drawWithBackground = msgItem.mBoxId == Mms.MESSAGE_BOX_INBOX;

        mBodyTextView.setText(formatMessage(msgItem.mContact, null, msgItem.mSubject,
                                            msgSizeText + "\n" + msgItem.mTimestamp,
                                            drawWithBackground));

        int state = DownloadManager.getInstance().getState(msgItem.mMessageUri);
        switch (state) {
            case DownloadManager.STATE_DOWNLOADING:
                inflateDownloadControls();
                mDownloadingLabel.setVisibility(View.VISIBLE);
                mDownloadButton.setVisibility(View.GONE);
                break;
            case DownloadManager.STATE_UNSTARTED:
            case DownloadManager.STATE_TRANSIENT_FAILURE:
            case DownloadManager.STATE_PERMANENT_FAILURE:
            default:
                setLongClickable(true);
                inflateDownloadControls();
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
        if (mDownloadButton != null) {
            mDownloadButton.setVisibility(View.GONE);
            mDownloadingLabel.setVisibility(View.GONE);
        }
        // Since the message text should be concatenated with the sender's
        // address(or name), I have to display it here instead of
        // displaying it by the Presenter.
        mBodyTextView.setTransformationMethod(HideReturnsTransformationMethod.getInstance());

        boolean drawWithBackground = msgItem.mBoxId == Mms.MESSAGE_BOX_INBOX;

        mBodyTextView.setText(formatMessage(msgItem.mContact, msgItem.mBody,
                                            msgItem.mSubject, msgItem.mTimestamp,
                                            drawWithBackground));
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
    
    private void inflateDownloadControls() {
        if (mDownloadButton == null) {
            //inflate the download controls
            findViewById(R.id.mms_downloading_view_stub).setVisibility(VISIBLE);
            mDownloadButton = (Button) findViewById(R.id.btn_download_msg);
            mDownloadingLabel = (TextView) findViewById(R.id.label_downloading);
        }
    }
    
    private CharSequence formatMessage(String contact, String body, String subject,
                                       String timestamp, boolean drawBackground) {
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
            SmileyResources smileyResources = new SmileyResources(
                    getResources().getStringArray(DEFAULT_SMILEY_TEXTS), DEFAULT_SMILEY_RES_IDS);
            SmileyParser smileyParser = new SmileyParser(body, smileyResources);
            smileyParser.parse();
            buf.append(smileyParser.getSpannableString(mContext));
        }

        buf.append("\n");
        int startOffset = buf.length();
        
        // put a one pixel high spacer line between the message and the time stamp as requested
        // by the spec.
        buf.append("\n");
        buf.setSpan(new AbsoluteSizeSpan(3), startOffset, buf.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        startOffset = buf.length();
        buf.append(timestamp);
        buf.setSpan(new AbsoluteSizeSpan(12), startOffset, buf.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        // Make the timestamp text not as dark
        int color = mContext.getResources().getColor(R.color.timestamp_color);
        buf.setSpan(new ForegroundColorSpan(color), startOffset, buf.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        // For now, they've decided not to draw a darker background behind the timestamp.
        // Keep the code for now.
//        if (drawBackground) {
//            int color = mContext.getResources().getColor(R.color.timestamp_color);
//            buf.setSpan(new Background(color), startOffset, buf.length(),
//                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//        }
        return buf;
    }

    private static class Background implements LineBackgroundSpan {
        private int mColor;
        
        public Background(int color) {
            mColor = color;
        }

        public void drawBackground(Canvas c, Paint p,
                int left, int right,
                int top, int baseline, int bottom,
                CharSequence text, int start, int end,
                int lnum) {
            int col = p.getColor();
            Paint.Style s = p.getStyle();

            p.setColor(mColor);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(left, top, right, bottom, p);

            p.setColor(col);
            p.setStyle(s);
        }
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
            final java.util.ArrayList<String> urls = MessageUtils.extractUris(spans);

            ArrayAdapter<String> adapter = 
                new ArrayAdapter<String>(mContext, android.R.layout.select_dialog_item, urls) {
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    try {
                        String url = getItem(position).toString();
                        TextView tv = (TextView) v;
                        Drawable d = mContext.getPackageManager().getActivityIcon(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        if (d != null) {
                            d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
                            tv.setCompoundDrawablePadding(10);
                            tv.setCompoundDrawables(d, null, null, null);
                        }
                        final String telPrefix = "tel:";
                        if (url.startsWith(telPrefix)) {
                            url = PhoneNumberUtils.formatNumber(url.substring(telPrefix.length()));
                        }
                        tv.setText(url);
                    } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                        ;
                    }
                    return v;
                }
            };

            AlertDialog.Builder b = new AlertDialog.Builder(mContext);

            DialogInterface.OnClickListener click = new DialogInterface.OnClickListener() {
                public final void onClick(DialogInterface dialog, int which) {
                    if (which >= 0) {
                        Uri uri = Uri.parse(urls.get(which));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.addCategory(Intent.CATEGORY_BROWSABLE);
                        mContext.startActivity(intent);
                    }
                }
            };
                
            b.setTitle(R.string.select_link_title);
            b.setCancelable(true);
            b.setAdapter(adapter, click);

            b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public final void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            b.show();
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

    private void drawLeftStatusIndicator(int msgBoxId) {
        switch (msgBoxId) {
            case Mms.MESSAGE_BOX_INBOX:
                mMsgListItem.setBackgroundResource(R.drawable.listitem_background_lightblue);
                break;

            case Mms.MESSAGE_BOX_DRAFTS:
            case Sms.MESSAGE_TYPE_FAILED:
            case Sms.MESSAGE_TYPE_QUEUED:
            case Mms.MESSAGE_BOX_OUTBOX:
                mMsgListItem.setBackgroundResource(R.drawable.listitem_background);
                break;

            default:
                mMsgListItem.setBackgroundResource(R.drawable.listitem_background);
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
