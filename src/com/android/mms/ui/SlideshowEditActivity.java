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

import com.google.android.mms.MmsException;
import com.android.mms.R;
import com.android.mms.model.IModelChangedObserver;
import com.android.mms.model.Model;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;

import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduPersister;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Config;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A list of slides which allows user to edit each item in it.
 */
public class SlideshowEditActivity extends ListActivity {
    private final static String TAG = "SlideshowEditActivity";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    // Menu ids.
    private final static int MENU_MOVE_UP           = 0;
    private final static int MENU_MOVE_DOWN         = 1;
    private final static int MENU_REMOVE_SLIDE      = 2;
    private final static int MENU_ADD_SLIDE         = 3;
    private final static int MENU_DISCARD_SLIDESHOW = 4;

    private final static int REQUEST_CODE_EDIT_SLIDE         = 6;

    // State.
    private final static String STATE = "state";
    private final static String SLIDE_INDEX = "slide_index";
    private final static String MESSAGE_URI = "message_uri";

    private ListView mList;
    private SlideListAdapter mSlideListAdapter;

    private SlideshowModel mSlideshowModel = null;
    private SlideshowEditor mSlideshowEditor = null;

    private Bundle mState;
    private Uri mUri;
    private Intent mResultIntent;
    private boolean mDirty;
    private View mAddSlideItem;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mList = getListView();
        mAddSlideItem = createAddSlideItem();
        mList.addFooterView(mAddSlideItem);
        mAddSlideItem.setVisibility(View.GONE);

        if (icicle != null) {
            // Retrieve previously saved state of this activity.
            mState = icicle.getBundle(STATE);
        }

        if (mState != null) {
            mUri = Uri.parse(mState.getString(MESSAGE_URI));
        } else {
            mUri = getIntent().getData();
        }

        if (mUri == null) {
            Log.e(TAG, "Cannot startup activity, null Uri.");
            finish();
            return;
        }

        // Return the Uri of the message to whoever invoked us.
        mResultIntent = new Intent();
        mResultIntent.setData(mUri);

        try {
            initSlideList();
            adjustAddSlideVisibility();
        } catch (MmsException e) {
            Log.e(TAG, "Failed to initialize the slide-list.", e);
            finish();
        }
    }

    private View createAddSlideItem() {
        View v = ((LayoutInflater) getSystemService(
                Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.slideshow_edit_item, null);

        //  Add slide.
        TextView text = (TextView) v.findViewById(R.id.slide_number_text);
        text.setText(R.string.add_slide);

        text = (TextView) v.findViewById(R.id.text_preview);
        text.setText(R.string.add_slide_hint);
        text.setVisibility(View.VISIBLE);

        ImageView image = (ImageView) v.findViewById(R.id.image_preview);
        image.setImageResource(R.drawable.ic_launcher_slideshow_add_sms);

        return v;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (position == (l.getCount() - 1)) {
            addNewSlide();
        } else {
            openSlide(position);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mState != null) {
            mList.setSelection(mState.getInt(SLIDE_INDEX, 0));
        }
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mState = new Bundle();
        if (mList.getSelectedItemPosition() >= 0) {
            mState.putInt(SLIDE_INDEX, mList.getSelectedItemPosition());
        }

        if (mUri != null) {
            mState.putString(MESSAGE_URI, mUri.toString());
        }

        if (LOCAL_LOGV) {
            Log.v(TAG, "Saving state: " + mState);
        }
        outState.putBundle(STATE, mState);
    }

    @Override
    protected void onPause()  {
        super.onPause();

        synchronized (this) {
            if (mDirty) {
                try {
                    PduBody pb = mSlideshowModel.toPduBody();
                    PduPersister.getPduPersister(this).updateParts(mUri, pb);
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
        cleanupSlideshowModel();
    }

    private void cleanupSlideshowModel() {
        if (mSlideshowModel != null) {
            mSlideshowModel.unregisterModelChangedObserver(
                    mModelChangedObserver);
            mSlideshowModel = null;
        }
    }

    private void initSlideList() throws MmsException {
        cleanupSlideshowModel();
        mSlideshowModel = SlideshowModel.createFromMessageUri(this, mUri);
        mSlideshowModel.registerModelChangedObserver(mModelChangedObserver);
        mSlideshowEditor = new SlideshowEditor(this, mSlideshowModel);
        mSlideListAdapter = new SlideListAdapter(
                this, R.layout.slideshow_edit_item, mSlideshowModel);
        mList.setAdapter(mSlideListAdapter);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        int position = mList.getSelectedItemPosition();
        if ((position >= 0) && (position != (mList.getCount() - 1))) {
            // Selected one slide.
            if (position > 0) {
                menu.add(0, MENU_MOVE_UP, 0, R.string.move_up).setIcon(R.drawable.ic_menu_move_up);
            }

            if (position < (mSlideListAdapter.getCount() - 1)) {
                menu.add(0, MENU_MOVE_DOWN, 0, R.string.move_down).setIcon(
                        R.drawable.ic_menu_move_down);
            }

            menu.add(0, MENU_ADD_SLIDE, 0, R.string.add_slide).setIcon(R.drawable.ic_menu_add_slide);

            menu.add(0, MENU_REMOVE_SLIDE, 0, R.string.remove_slide).setIcon(
                    android.R.drawable.ic_menu_delete);
        } else {
            menu.add(0, MENU_ADD_SLIDE, 0, R.string.add_slide).setIcon(R.drawable.ic_menu_add_slide);
        }

        menu.add(0, MENU_DISCARD_SLIDESHOW, 0,
                R.string.discard_slideshow).setIcon(R.drawable.ic_menu_delete_played);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int position = mList.getSelectedItemPosition();

        switch (item.getItemId()) {
            case MENU_MOVE_UP:
                if ((position > 0) && (position < mSlideshowModel.size())) {
                    mSlideshowEditor.moveSlideUp(position);
                    mSlideListAdapter.notifyDataSetChanged();
                    mList.setSelection(position - 1);
                }
                break;
            case MENU_MOVE_DOWN:
                if ((position >= 0) && (position < mSlideshowModel.size() - 1)) {
                    mSlideshowEditor.moveSlideDown(position);
                    mSlideListAdapter.notifyDataSetChanged();
                    mList.setSelection(position + 1);
                }
                break;
            case MENU_REMOVE_SLIDE:
                if ((position >= 0) && (position < mSlideshowModel.size())) {
                    mSlideshowEditor.removeSlide(position);
                    mSlideListAdapter.notifyDataSetChanged();
                }
                break;
            case MENU_ADD_SLIDE:
                addNewSlide();
                break;
            case MENU_DISCARD_SLIDESHOW:
                // delete all slides from slideshow.
                mSlideshowEditor.removeAllSlides();
                mSlideListAdapter.notifyDataSetChanged();
                finish();
                break;
        }

        return true;
    }

    private void openSlide(int index) {
        Intent intent = new Intent(this, SlideEditorActivity.class);
        intent.setData(mUri);
        intent.putExtra(SlideEditorActivity.SLIDE_INDEX, index);
        startActivityForResult(intent, REQUEST_CODE_EDIT_SLIDE);
    }

    private void adjustAddSlideVisibility() {
        if (mSlideshowModel.size() >= SlideshowEditor.MAX_SLIDE_NUM) {
            mAddSlideItem.setVisibility(View.GONE);
        } else {
            mAddSlideItem.setVisibility(View.VISIBLE);
        }
    }

    private void addNewSlide() {
        if ( mSlideshowEditor.addNewSlide() ) {
            // add successfully
            mSlideListAdapter.notifyDataSetChanged();

            // Select the new slide.
            mList.requestFocus();
            mList.setSelection(mSlideshowModel.size() - 1);
        } else {
            Toast.makeText(this, R.string.cannot_add_slide_anymore,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }

        switch(requestCode) {
            case REQUEST_CODE_EDIT_SLIDE:
                synchronized (this) {
                    mDirty = true;
                }
                setResult(RESULT_OK, mResultIntent);

                if ((data != null) && data.getBooleanExtra("done", false)) {
                    finish();
                    return;
                }

                try {
                    initSlideList();
                } catch (MmsException e) {
                    Log.e(TAG, "Failed to initialize the slide-list.", e);
                    finish();
                    return;
                }
                break;
        }
    }

    private static class SlideListAdapter extends ArrayAdapter<SlideModel> {
        private final Context mContext;
        private final int mResource;
        private final LayoutInflater mInflater;
        private final SlideshowModel mSlideshow;

        public SlideListAdapter(Context context, int resource,
                SlideshowModel slideshow) {
            super(context, resource, slideshow);

            mContext = context;
            mResource = resource;
            mInflater = LayoutInflater.from(context);
            mSlideshow = slideshow;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return createViewFromResource(position, convertView, mResource);
        }

        private View createViewFromResource(int position, View convertView, int resource) {
            SlideListItemView slideListItemView;
            slideListItemView = (SlideListItemView) mInflater.inflate(
                    resource, null);

            // Show slide number.
            TextView text;
            text = (TextView) slideListItemView.findViewById(R.id.slide_number_text);
            text.setText(mContext.getString(R.string.slide_number, position + 1));

            SlideModel slide = getItem(position);
            int dur = slide.getDuration() / 1000;
            text = (TextView) slideListItemView.findViewById(R.id.duration_text);
            text.setText(mContext.getResources().
                         getQuantityString(R.plurals.slide_duration, dur, dur));

            Presenter presenter = PresenterFactory.getPresenter(
                    "SlideshowPresenter", mContext, slideListItemView, mSlideshow);
            ((SlideshowPresenter) presenter).setLocation(position);
            presenter.present();

            return slideListItemView;
        }
    }

    private final IModelChangedObserver mModelChangedObserver =
        new IModelChangedObserver() {
            public void onModelChanged(Model model, boolean dataChanged) {
                synchronized (SlideshowEditActivity.this) {
                    mDirty = true;
                }
                setResult(RESULT_OK, mResultIntent);
                adjustAddSlideVisibility();
            }
        };
}
