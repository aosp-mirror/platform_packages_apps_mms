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
import com.android.mms.util.SmileyParser;

import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPersister;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.Browser;
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

        mBodyTextView.setText(formatMessage(msgItem.mContact, null, msgItem.mSubject,
                                            msgSizeText + "\n" + msgItem.mTimestamp));

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

        // Get and/or lazily set the formatted message from/on the
        // MessageItem.  Because the MessageItem instances come from a
        // cache (currently of size ~50), the hit rate on avoiding the
        // expensive formatMessage() call is very high.
        CharSequence formattedMessage = msgItem.getCachedFormattedMessage();
        if (formattedMessage == null) {
            formattedMessage = formatMessage(msgItem.mContact, msgItem.mBody,
                                             msgItem.mSubject, msgItem.mTimestamp);
            msgItem.setCachedFormattedMessage(formattedMessage);
        }
        mBodyTextView.setText(formattedMessage);

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

        if (null == bitmap) {
            bitmap = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_missing_thumbnail_picture);
        }
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
                                       String timestamp) {
        CharSequence template = mContext.getResources().getText(R.string.name_colon);
        SpannableStringBuilder buf = 
            new SpannableStringBuilder(TextUtils.replace(template,
                new String[] { "%s" },
                new CharSequence[] { contact }));

        boolean hasSubject = !TextUtils.isEmpty(subject);
        if (hasSubject) {
            buf.append(mContext.getResources().getString(R.string.inline_subject, subject));
        }

        if (!TextUtils.isEmpty(body)) {
            if (hasSubject) {
                buf.append(" - ");
            }
            SmileyParser parser = SmileyParser.getInstance();
            buf.append(parser.addSmileySpans(body));
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
            case AttachmentEditor.VIDEO_ATTACHMENT:
            case AttachmentEditor.AUDIO_ATTACHMENT:
            case AttachmentEditor.SLIDESHOW_ATTACHMENT:
                MessageUtils.viewMmsMessageAttachment(mContext,
                        mi.mMessageUri, mi.mSlideshow, null /* persister */);
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
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, mContext.getPackageName());

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
                        intent.putExtra(Browser.EXTRA_APPLICATION_ID, mContext.getPackageName());
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
        switch(msgItem.mAttachmentType) {
        case AttachmentEditor.IMAGE_ATTACHMENT:
        case AttachmentEditor.VIDEO_ATTACHMENT:
            mImageView.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    MessageUtils.viewMmsMessageAttachment(mContext,
                        null /* uri */, msgItem.mSlideshow, null /* persister */);
                }
            });
            mImageView.setOnLongClickListener(new OnLongClickListener() {
                public boolean onLongClick(View v) {
                    return v.showContextMenu();
                }
            });
            break;
            
        default:
            mImageView.setOnClickListener(null);
            break;
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

    public static boolean isFailedMessage(MessageItem msgItem) {
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
                mRightStatusIndicator.setImageResource(R.drawable.ic_sms_mms_not_delivered);
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
        Bitmap bitmap = VideoAttachmentView.createVideoThumbnail(mContext, video);
        if (null == bitmap) {
            bitmap = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_missing_thumbnail_video);
        }
        mImageView.setImageBitmap(bitmap);
        mImageView.setVisibility(VISIBLE);
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
