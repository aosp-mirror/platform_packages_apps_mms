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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.mms.ExceedMessageSizeException;
import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.ResolutionException;
import com.android.mms.TempFileProvider;
import com.android.mms.UnsupportContentTypeException;
import com.android.mms.model.IModelChangedObserver;
import com.android.mms.model.LayoutModel;
import com.android.mms.model.Model;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.ui.BasicSlideEditorView.OnTextChangedListener;
import com.android.mms.ui.MessageUtils.ResizeImageResultCallback;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;

/**
 * This activity allows user to edit the contents of a slide.
 */
public class SlideEditorActivity extends Activity {
    private static final String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = false;

    // Key for extra data.
    public static final String SLIDE_INDEX = "slide_index";

    // Menu ids.
    private final static int MENU_REMOVE_TEXT       = 0;
    private final static int MENU_ADD_PICTURE       = 1;
    private final static int MENU_TAKE_PICTURE      = 2;
    private final static int MENU_DEL_PICTURE       = 3;
    private final static int MENU_ADD_AUDIO         = 4;
    private final static int MENU_DEL_AUDIO         = 5;
    private final static int MENU_ADD_VIDEO         = 6;
    private final static int MENU_ADD_SLIDE         = 7;
    private final static int MENU_DEL_VIDEO         = 8;
    private final static int MENU_LAYOUT            = 9;
    private final static int MENU_DURATION          = 10;
    private final static int MENU_PREVIEW_SLIDESHOW = 11;
    private final static int MENU_RECORD_SOUND      = 12;
    private final static int MENU_SUB_AUDIO         = 13;
    private final static int MENU_TAKE_VIDEO        = 14;

    // Request code.
    private final static int REQUEST_CODE_EDIT_TEXT          = 0;
    private final static int REQUEST_CODE_CHANGE_PICTURE     = 1;
    private final static int REQUEST_CODE_TAKE_PICTURE       = 2;
    private final static int REQUEST_CODE_CHANGE_MUSIC       = 3;
    private final static int REQUEST_CODE_RECORD_SOUND       = 4;
    private final static int REQUEST_CODE_CHANGE_VIDEO       = 5;
    private final static int REQUEST_CODE_CHANGE_DURATION    = 6;
    private final static int REQUEST_CODE_TAKE_VIDEO         = 7;

    // number of items in the duration selector dialog that directly map from
    // item index to duration in seconds (duration = index + 1)
    private final static int NUM_DIRECT_DURATIONS = 10;

    private ImageButton mNextSlide;
    private ImageButton mPreSlide;
    private Button mPreview;
    private Button mReplaceImage;
    private Button mRemoveSlide;
    private EditText mTextEditor;
    private Button mDone;
    private BasicSlideEditorView mSlideView;

    private SlideshowModel mSlideshowModel;
    private SlideshowEditor mSlideshowEditor;
    private SlideshowPresenter mPresenter;
    private boolean mDirty;

    private int mPosition;
    private Uri mUri;

    private final static String MESSAGE_URI = "message_uri";
    private AsyncDialog mAsyncDialog;   // Used for background tasks.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_slide_activity);

        mSlideView = (BasicSlideEditorView) findViewById(R.id.slide_editor_view);
        mSlideView.setOnTextChangedListener(mOnTextChangedListener);

        mPreSlide = (ImageButton) findViewById(R.id.pre_slide_button);
        mPreSlide.setOnClickListener(mOnNavigateBackward);

        mNextSlide = (ImageButton) findViewById(R.id.next_slide_button);
        mNextSlide.setOnClickListener(mOnNavigateForward);

        mPreview = (Button) findViewById(R.id.preview_button);
        mPreview.setOnClickListener(mOnPreview);

        mReplaceImage = (Button) findViewById(R.id.replace_image_button);
        mReplaceImage.setOnClickListener(mOnReplaceImage);

        mRemoveSlide = (Button) findViewById(R.id.remove_slide_button);
        mRemoveSlide.setOnClickListener(mOnRemoveSlide);

        mTextEditor = (EditText) findViewById(R.id.text_message);
        mTextEditor.setFilters(new InputFilter[] {
                new LengthFilter(MmsConfig.getMaxTextLimit())});

        mDone = (Button) findViewById(R.id.done_button);
        mDone.setOnClickListener(mDoneClickListener);

        initActivityState(savedInstanceState, getIntent());

        try {
            mSlideshowModel = SlideshowModel.createFromMessageUri(this, mUri);
            // Confirm that we have at least 1 slide to display
            if (mSlideshowModel.size() == 0) {
                Log.e(TAG, "Loaded slideshow is empty; can't edit nothingness, exiting.");
                finish();
                return;
            }
            // Register an observer to watch whether the data model is changed.
            mSlideshowModel.registerModelChangedObserver(mModelChangedObserver);
            mSlideshowEditor = new SlideshowEditor(this, mSlideshowModel);
            mPresenter = (SlideshowPresenter) PresenterFactory.getPresenter(
                    "SlideshowPresenter", this, mSlideView, mSlideshowModel);

            // Sanitize mPosition
            if (mPosition >= mSlideshowModel.size()) {
                mPosition = Math.max(0, mSlideshowModel.size() - 1);
            } else if (mPosition < 0) {
                mPosition = 0;
            }

            showCurrentSlide();
        } catch (MmsException e) {
            Log.e(TAG, "Create SlideshowModel failed!", e);
            finish();
            return;
        }
    }

    private void initActivityState(Bundle savedInstanceState, Intent intent) {
        if (savedInstanceState != null) {
            mUri = (Uri) savedInstanceState.getParcelable(MESSAGE_URI);
            mPosition = savedInstanceState.getInt(SLIDE_INDEX, 0);
        } else {
            mUri = intent.getData();
            mPosition = intent.getIntExtra(SLIDE_INDEX, 0);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(SLIDE_INDEX, mPosition);
        outState.putParcelable(MESSAGE_URI, mUri);
    }

    @Override
    protected void onPause()  {
        super.onPause();

        // remove any callback to display a progress spinner
        if (mAsyncDialog != null) {
            mAsyncDialog.clearPendingProgressDialog();
        }

        synchronized (this) {
            if (mDirty) {
                try {
                    PduBody pb = mSlideshowModel.toPduBody();
                    PduPersister.getPduPersister(this).updateParts(mUri, pb, null);
                    mSlideshowModel.sync(pb);
                }  catch (MmsException e) {
                    Log.e(TAG, "Cannot update the message: " + mUri, e);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mSlideshowModel != null) {
            mSlideshowModel.unregisterModelChangedObserver(
                    mModelChangedObserver);
        }
    }

    private final IModelChangedObserver mModelChangedObserver =
        new IModelChangedObserver() {
            public void onModelChanged(Model model, boolean dataChanged) {
                synchronized (SlideEditorActivity.this) {
                    mDirty = true;
                }
                setResult(RESULT_OK);
            }
        };

    private final OnClickListener mOnRemoveSlide = new OnClickListener() {
        public void onClick(View v) {
            // Validate mPosition
            if (mPosition >= 0 && mPosition < mSlideshowModel.size()) {
                mSlideshowEditor.removeSlide(mPosition);
                int size = mSlideshowModel.size();
                if (size > 0) {
                    if (mPosition >= size) {
                        mPosition--;
                    }
                    showCurrentSlide();
                } else {
                    finish();
                    return;
                }
            }
        }
    };

    private final OnTextChangedListener mOnTextChangedListener = new OnTextChangedListener() {
        public void onTextChanged(String s) {
            if (!isFinishing()) {
                mSlideshowEditor.changeText(mPosition, s);
            }
        }
    };

    private final OnClickListener mOnPreview = new OnClickListener() {
        public void onClick(View v) {
            previewSlideshow();
        }
    };

    private final OnClickListener mOnReplaceImage = new OnClickListener() {
        public void onClick(View v) {
            SlideModel slide = mSlideshowModel.get(mPosition);
            if (slide != null && slide.hasVideo()) {
                Toast.makeText(SlideEditorActivity.this, R.string.cannot_add_picture_and_video,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
            intent.setType(ContentType.IMAGE_UNSPECIFIED);
            startActivityForResult(intent, REQUEST_CODE_CHANGE_PICTURE);
        }
    };

    private final OnClickListener mOnNavigateBackward = new OnClickListener() {
        public void onClick(View v) {
            if (mPosition > 0) {
                mPosition --;
                showCurrentSlide();
            }
        }
    };

    private final OnClickListener mOnNavigateForward = new OnClickListener() {
        public void onClick(View v) {
            if (mPosition < mSlideshowModel.size() - 1) {
                mPosition ++;
                showCurrentSlide();
            }
        }
    };

    private final OnClickListener mDoneClickListener = new OnClickListener() {
        public void onClick(View v) {
            Intent data = new Intent();
            data.putExtra("done", true);
            setResult(RESULT_OK, data);
            finish();
        }
    };

    private AsyncDialog getAsyncDialog() {
        if (mAsyncDialog == null) {
            mAsyncDialog = new AsyncDialog(this);
        }
        return mAsyncDialog;
    }

    private void previewSlideshow() {
        MessageUtils.viewMmsMessageAttachment(SlideEditorActivity.this, mUri, mSlideshowModel,
                getAsyncDialog());
    }

    private void updateTitle() {
        setTitle(getString(R.string.slide_show_part, (mPosition + 1), mSlideshowModel.size()));
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return false;
        }
        menu.clear();

        SlideModel slide = mSlideshowModel.get(mPosition);

        if (slide == null) {
            return false;
        }

        // Preview slideshow.
        menu.add(0, MENU_PREVIEW_SLIDESHOW, 0, R.string.preview_slideshow).setIcon(
                com.android.internal.R.drawable.ic_menu_play_clip);

        // Text
        if (slide.hasText() && !TextUtils.isEmpty(slide.getText().getText())) {
            //"Change text" if text is set.
            menu.add(0, MENU_REMOVE_TEXT, 0, R.string.remove_text).setIcon(
                    R.drawable.ic_menu_remove_text);
        }

        // Picture
        if (slide.hasImage()) {
            menu.add(0, MENU_DEL_PICTURE, 0, R.string.remove_picture).setIcon(
                    R.drawable.ic_menu_remove_picture);
        } else if (!slide.hasVideo()) {
            menu.add(0, MENU_ADD_PICTURE, 0, R.string.add_picture).setIcon(
                    R.drawable.ic_menu_picture);
            menu.add(0, MENU_TAKE_PICTURE, 0, R.string.attach_take_photo).setIcon(
                    R.drawable.ic_menu_picture);
        }

        // Music
        if (slide.hasAudio()) {
            menu.add(0, MENU_DEL_AUDIO, 0, R.string.remove_music).setIcon(
                    R.drawable.ic_menu_remove_sound);
        } else if (!slide.hasVideo()) {
            if (MmsConfig.getAllowAttachAudio()) {
                SubMenu subMenu = menu.addSubMenu(0, MENU_SUB_AUDIO, 0, R.string.add_music)
                    .setIcon(R.drawable.ic_menu_add_sound);
                subMenu.add(0, MENU_ADD_AUDIO, 0, R.string.attach_sound);
                subMenu.add(0, MENU_RECORD_SOUND, 0, R.string.attach_record_sound);
            } else {
                menu.add(0, MENU_RECORD_SOUND, 0, R.string.attach_record_sound)
                    .setIcon(R.drawable.ic_menu_add_sound);
            }
        }

        // Video
        if (slide.hasVideo()) {
            menu.add(0, MENU_DEL_VIDEO, 0, R.string.remove_video).setIcon(
                    R.drawable.ic_menu_remove_video);
        } else if (!slide.hasAudio() && !slide.hasImage()) {
            menu.add(0, MENU_ADD_VIDEO, 0, R.string.add_video).setIcon(R.drawable.ic_menu_movie);
            menu.add(0, MENU_TAKE_VIDEO, 0, R.string.attach_record_video)
                .setIcon(R.drawable.ic_menu_movie);
        }

        // Add slide
        menu.add(0, MENU_ADD_SLIDE, 0, R.string.add_slide).setIcon(
                R.drawable.ic_menu_add_slide);

        // Slide duration
        String duration = getResources().getString(R.string.duration_sec);
        menu.add(0, MENU_DURATION, 0,
                duration.replace("%s", String.valueOf(slide.getDuration() / 1000))).setIcon(
                        R.drawable.ic_menu_duration);

        // Slide layout
        int resId;
        if (mSlideshowModel.getLayout().getLayoutType() == LayoutModel.LAYOUT_TOP_TEXT) {
            resId = R.string.layout_top;
        } else {
            resId = R.string.layout_bottom;
        }
        // FIXME: set correct icon when layout icon is available.
        menu.add(0, MENU_LAYOUT, 0, resId).setIcon(R.drawable.ic_menu_picture);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PREVIEW_SLIDESHOW:
                previewSlideshow();
                break;

            case MENU_REMOVE_TEXT:
                SlideModel slide = mSlideshowModel.get(mPosition);
                if (slide != null) {
                    slide.removeText();
                }
                break;

            case MENU_ADD_PICTURE:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
                intent.setType(ContentType.IMAGE_UNSPECIFIED);
                startActivityForResult(intent, REQUEST_CODE_CHANGE_PICTURE);
                break;

            case MENU_TAKE_PICTURE:
                MessageUtils.capturePicture(this, REQUEST_CODE_TAKE_PICTURE);
                break;

            case MENU_DEL_PICTURE:
                mSlideshowEditor.removeImage(mPosition);
                setReplaceButtonText(R.string.add_picture);
                break;

            case MENU_ADD_AUDIO:
                MessageUtils.selectAudio(this, REQUEST_CODE_CHANGE_MUSIC);
                break;

            case MENU_RECORD_SOUND:
                slide = mSlideshowModel.get(mPosition);
                int currentSlideSize = slide.getSlideSize();
                long sizeLimit = ComposeMessageActivity.computeAttachmentSizeLimit(mSlideshowModel,
                        currentSlideSize);
                MessageUtils.recordSound(this, REQUEST_CODE_RECORD_SOUND, sizeLimit);
                break;

            case MENU_DEL_AUDIO:
                mSlideshowEditor.removeAudio(mPosition);
                break;

            case MENU_ADD_VIDEO:
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(ContentType.VIDEO_UNSPECIFIED);
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(intent, REQUEST_CODE_CHANGE_VIDEO);
                break;

            case MENU_TAKE_VIDEO:
                slide = mSlideshowModel.get(mPosition);
                currentSlideSize = slide.getSlideSize();
                sizeLimit = ComposeMessageActivity.computeAttachmentSizeLimit(mSlideshowModel,
                        currentSlideSize);
                if (sizeLimit > 0) {
                    MessageUtils.recordVideo(this, REQUEST_CODE_TAKE_VIDEO, sizeLimit);
                } else {
                    Toast.makeText(this,
                            getString(R.string.message_too_big_for_video),
                            Toast.LENGTH_SHORT).show();
                }
                break;

            case MENU_DEL_VIDEO:
                mSlideshowEditor.removeVideo(mPosition);
                break;

            case MENU_ADD_SLIDE:
                mPosition++;
                if ( mSlideshowEditor.addNewSlide(mPosition) ) {
                    // add successfully
                    showCurrentSlide();
                } else {
                    // move position back
                    mPosition--;
                    Toast.makeText(this, R.string.cannot_add_slide_anymore,
                            Toast.LENGTH_SHORT).show();
                }
                break;

            case MENU_LAYOUT:
                showLayoutSelectorDialog();
                break;

            case MENU_DURATION:
                showDurationDialog();
                break;
        }

        return true;
    }

    private void setReplaceButtonText(int text) {
        mReplaceImage.setText(text);
    }

    private void showDurationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_mms_duration);
        String title = getResources().getString(R.string.duration_selector_title);
        builder.setTitle(title + (mPosition + 1) + "/" + mSlideshowModel.size());

        builder.setItems(R.array.select_dialog_items,
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if ((which >= 0) && (which < NUM_DIRECT_DURATIONS)) {
                    mSlideshowEditor.changeDuration(
                            mPosition, (which + 1) * 1000);
                } else {
                    Intent intent = new Intent(SlideEditorActivity.this,
                            EditSlideDurationActivity.class);
                    intent.putExtra(EditSlideDurationActivity.SLIDE_INDEX, mPosition);
                    intent.putExtra(EditSlideDurationActivity.SLIDE_TOTAL,
                            mSlideshowModel.size());
                    intent.putExtra(EditSlideDurationActivity.SLIDE_DUR,
                            mSlideshowModel.get(mPosition).getDuration() / 1000); // in seconds
                    startActivityForResult(intent, REQUEST_CODE_CHANGE_DURATION);
                }
                dialog.dismiss();
            }
        });

        builder.show();
    }

    private void showLayoutSelectorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_mms_layout);

        String title = getResources().getString(R.string.layout_selector_title);
        builder.setTitle(title + (mPosition + 1) + "/" + mSlideshowModel.size());

        LayoutSelectorAdapter adapter = new LayoutSelectorAdapter(this);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: // Top text.
                        mSlideshowEditor.changeLayout(LayoutModel.LAYOUT_TOP_TEXT);
                        break;
                    case 1: // Bottom text.
                        mSlideshowEditor.changeLayout(LayoutModel.LAYOUT_BOTTOM_TEXT);
                        break;
                }
                dialog.dismiss();
            }
        });

        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }

        switch(requestCode) {
            case REQUEST_CODE_EDIT_TEXT:
                // XXX where does this come from?  Action is probably not the
                // right place to have the text...
                mSlideshowEditor.changeText(mPosition, data.getAction());
                break;

            case REQUEST_CODE_TAKE_PICTURE:
                Uri pictureUri = null;
                boolean showError = false;
                try {
                    pictureUri = TempFileProvider.renameScrapFile(".jpg",
                            Integer.toString(mPosition), this);

                    if (pictureUri == null) {
                        showError = true;
                    } else {
                        // Remove the old captured picture's thumbnail from the cache
                        MmsApp.getApplication().getThumbnailManager().removeThumbnail(pictureUri);

                        mSlideshowEditor.changeImage(mPosition, pictureUri);
                        setReplaceButtonText(R.string.replace_image);
                    }
                } catch (MmsException e) {
                    Log.e(TAG, "add image failed", e);
                    showError = true;
                } catch (UnsupportContentTypeException e) {
                    MessageUtils.showErrorDialog(SlideEditorActivity.this,
                            getResourcesString(R.string.unsupported_media_format, getPictureString()),
                            getResourcesString(R.string.select_different_media, getPictureString()));
                } catch (ResolutionException e) {
                    MessageUtils.resizeImageAsync(this, pictureUri, new Handler(),
                            mResizeImageCallback, false);
                } catch (ExceedMessageSizeException e) {
                    MessageUtils.resizeImageAsync(this, pictureUri, new Handler(),
                            mResizeImageCallback, false);
                }
                if (showError) {
                    notifyUser("add picture failed");
                    Toast.makeText(SlideEditorActivity.this,
                            getResourcesString(R.string.failed_to_add_media, getPictureString()),
                            Toast.LENGTH_SHORT).show();
                }
                break;

            case REQUEST_CODE_CHANGE_PICTURE:
                try {
                    mSlideshowEditor.changeImage(mPosition, data.getData());
                    setReplaceButtonText(R.string.replace_image);
                } catch (MmsException e) {
                    Log.e(TAG, "add image failed", e);
                    notifyUser("add picture failed");
                    Toast.makeText(SlideEditorActivity.this,
                            getResourcesString(R.string.failed_to_add_media, getPictureString()),
                            Toast.LENGTH_SHORT).show();
                } catch (UnsupportContentTypeException e) {
                    MessageUtils.showErrorDialog(SlideEditorActivity.this,
                            getResourcesString(R.string.unsupported_media_format, getPictureString()),
                            getResourcesString(R.string.select_different_media, getPictureString()));
                } catch (ResolutionException e) {
                    MessageUtils.resizeImageAsync(this, data.getData(), new Handler(),
                            mResizeImageCallback, false);
                } catch (ExceedMessageSizeException e) {
                    MessageUtils.resizeImageAsync(this, data.getData(), new Handler(),
                            mResizeImageCallback, false);
                }
                break;

            case REQUEST_CODE_CHANGE_MUSIC:
            case REQUEST_CODE_RECORD_SOUND:
                Uri uri;
                if (requestCode == REQUEST_CODE_CHANGE_MUSIC) {
                    uri = (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (Settings.System.DEFAULT_RINGTONE_URI.equals(uri)) {
                        return;
                    }
                } else {
                    uri = data.getData();
                }

                try {
                    mSlideshowEditor.changeAudio(mPosition, uri);
                } catch (MmsException e) {
                    Log.e(TAG, "add audio failed", e);
                    notifyUser("add music failed");
                    Toast.makeText(SlideEditorActivity.this,
                            getResourcesString(R.string.failed_to_add_media, getAudioString()),
                            Toast.LENGTH_SHORT).show();
                } catch (UnsupportContentTypeException e) {
                    MessageUtils.showErrorDialog(SlideEditorActivity.this,
                            getResourcesString(R.string.unsupported_media_format, getAudioString()),
                            getResourcesString(R.string.select_different_media, getAudioString()));
                } catch (ExceedMessageSizeException e) {
                    MessageUtils.showErrorDialog(SlideEditorActivity.this,
                            getResourcesString(R.string.exceed_message_size_limitation),
                            getResourcesString(R.string.failed_to_add_media, getAudioString()));
                }
                break;

            case REQUEST_CODE_TAKE_VIDEO:
                try {
                    Uri videoUri = TempFileProvider.renameScrapFile(".3gp",
                            Integer.toString(mPosition), this);

                    mSlideshowEditor.changeVideo(mPosition, videoUri);
                } catch (MmsException e) {
                    notifyUser("add video failed");
                    Toast.makeText(SlideEditorActivity.this,
                            getResourcesString(R.string.failed_to_add_media, getVideoString()),
                            Toast.LENGTH_SHORT).show();
                } catch (UnsupportContentTypeException e) {
                    MessageUtils.showErrorDialog(SlideEditorActivity.this,
                            getResourcesString(R.string.unsupported_media_format, getVideoString()),
                            getResourcesString(R.string.select_different_media, getVideoString()));
                } catch (ExceedMessageSizeException e) {
                    MessageUtils.showErrorDialog(SlideEditorActivity.this,
                            getResourcesString(R.string.exceed_message_size_limitation),
                            getResourcesString(R.string.failed_to_add_media, getVideoString()));
                }
                break;

            case REQUEST_CODE_CHANGE_VIDEO:
                try {
                    mSlideshowEditor.changeVideo(mPosition, data.getData());
                } catch (MmsException e) {
                    Log.e(TAG, "add video failed", e);
                    notifyUser("add video failed");
                    Toast.makeText(SlideEditorActivity.this,
                            getResourcesString(R.string.failed_to_add_media, getVideoString()),
                            Toast.LENGTH_SHORT).show();
                } catch (UnsupportContentTypeException e) {
                    MessageUtils.showErrorDialog(SlideEditorActivity.this,
                            getResourcesString(R.string.unsupported_media_format, getVideoString()),
                            getResourcesString(R.string.select_different_media, getVideoString()));
                } catch (ExceedMessageSizeException e) {
                    MessageUtils.showErrorDialog(SlideEditorActivity.this,
                            getResourcesString(R.string.exceed_message_size_limitation),
                            getResourcesString(R.string.failed_to_add_media, getVideoString()));
                }
                break;

            case REQUEST_CODE_CHANGE_DURATION:
                mSlideshowEditor.changeDuration(mPosition,
                    Integer.valueOf(data.getAction()) * 1000);
                break;
        }
    }

    private final ResizeImageResultCallback mResizeImageCallback = new ResizeImageResultCallback() {
        public void onResizeResult(PduPart part, boolean append) {
            Context context = SlideEditorActivity.this;
            if (part == null) {
                Toast.makeText(SlideEditorActivity.this,
                        getResourcesString(R.string.failed_to_add_media, getPictureString()),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                long messageId = ContentUris.parseId(mUri);
                PduPersister persister = PduPersister.getPduPersister(context);
                Uri newUri = persister.persistPart(part, messageId, null);
                mSlideshowEditor.changeImage(mPosition, newUri);

                setReplaceButtonText(R.string.replace_image);
            } catch (MmsException e) {
                notifyUser("add picture failed");
                Toast.makeText(SlideEditorActivity.this,
                        getResourcesString(R.string.failed_to_add_media, getPictureString()),
                        Toast.LENGTH_SHORT).show();
            } catch (UnsupportContentTypeException e) {
                MessageUtils.showErrorDialog(SlideEditorActivity.this,
                        getResourcesString(R.string.unsupported_media_format, getPictureString()),
                        getResourcesString(R.string.select_different_media, getPictureString()));
            } catch (ResolutionException e) {
                MessageUtils.showErrorDialog(SlideEditorActivity.this,
                        getResourcesString(R.string.failed_to_resize_image),
                        getResourcesString(R.string.resize_image_error_information));
            } catch (ExceedMessageSizeException e) {
                MessageUtils.showErrorDialog(SlideEditorActivity.this,
                        getResourcesString(R.string.exceed_message_size_limitation),
                        getResourcesString(R.string.failed_to_add_media, getPictureString()));
            }
        }
    };

    private String getResourcesString(int id, String mediaName) {
        Resources r = getResources();
        return r.getString(id, mediaName);
    }

    private String getResourcesString(int id) {
        Resources r = getResources();
        return r.getString(id);
    }

    private String getAudioString() {
        return getResourcesString(R.string.type_audio);
    }

    private String getPictureString() {
        return getResourcesString(R.string.type_picture);
    }

    private String getVideoString() {
        return getResourcesString(R.string.type_video);
    }

    private void notifyUser(String message) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "notifyUser: message=" + message);
        }
    }

    private void showCurrentSlide() {
        mPresenter.setLocation(mPosition);
        mPresenter.present(null);
        updateTitle();

        if (mSlideshowModel.get(mPosition).hasImage()) {
            setReplaceButtonText(R.string.replace_image);
        } else {
            setReplaceButtonText(R.string.add_picture);
        }
    }
}
