package com.hieuld.helium.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.hieuld.helium.R;
import com.hieuld.helium.util.Utils;

public class SearchBarView extends NavigationTintedToolbar implements
        TextWatcher, TextView.OnEditorActionListener,
        Toolbar.OnMenuItemClickListener,
        View.OnClickListener {

    private Context mContext;
    private MenuItem mClearItem;
    private MenuItem mVoiceItem;
    private EditText mQueryView;
    private SearchBarListener mListener;
    private Toolbar.OnMenuItemClickListener mExtraMenuListener;

    public interface SearchBarListener {
        void onSearchBack();
        void onSearchClearHistoryClicked();
        void onSearchQueryChanged(String query);
        void onSearchSubmitted(String query);
        void onSearchVoiceClicked();
    }

    public SearchBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        this.mContext = context;
        setNavigationIcon(R.drawable.ic_arrow_up);
        setNavigationContentDescription(R.string.action_search_up);

        int paddingHorizontal = Utils.dpToPx(this.mContext, 16);
        this.mQueryView = new EditText(this.mContext);
        this.mQueryView.setId(R.id.search_query);
        this.mQueryView.setBackground(null);

        this.mQueryView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_SEARCH);
        this.mQueryView.setInputType(InputType.TYPE_CLASS_TEXT);

        this.mQueryView.setTextSize(18.0f);
        this.mQueryView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);
        this.mQueryView.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary, android.R.color.black));
        this.mQueryView.setHintTextColor(resolveThemeColor(android.R.attr.textColorHint, R.color.text_hint));
        this.mQueryView.addTextChangedListener(this);
        this.mQueryView.setOnEditorActionListener(this);

        addView(this.mQueryView, new Toolbar.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setContentInsetStartWithNavigation(0);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SearchBarView, 0, 0);
            this.mQueryView.setHint(a.getString(R.styleable.SearchBarView_hint));
            a.recycle();
        }

        setOnMenuItemClickListener(this);
        setNavigationOnClickListener(this);
        inflateMenu(R.menu.search_bar);

        this.mClearItem = getMenu().findItem(R.id.clear);
        this.mVoiceItem = getMenu().findItem(R.id.voice);
        tintMenuIcons();
    }

    public void setListener(SearchBarListener listener) {
        this.mListener = listener;
    }

    public void addExtraMenuResource(int menuResId, Toolbar.OnMenuItemClickListener listener) {
        this.mExtraMenuListener = listener;
        inflateMenu(menuResId);
        tintMenuIcons();
    }

    private int resolveThemeColor(int attr, int fallbackResId) {
        TypedValue typedValue = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return ContextCompat.getColor(getContext(), typedValue.resourceId);
            }
            return typedValue.data;
        }
        return ContextCompat.getColor(getContext(), fallbackResId);
    }

    public void activate() {
        this.mQueryView.setText("");
        this.mQueryView.requestFocus();
        InputMethodManager imm = (InputMethodManager) this.mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(this.mQueryView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void deactivate() {
        InputMethodManager imm = (InputMethodManager) this.mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(this.mQueryView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public void setQueryAndSubmit(String query) {
        this.mQueryView.setText(query);
        this.mQueryView.setSelection(query.length());
        submit();
    }

    @Override
    public void onClick(View v) {
        if (this.mListener != null) {
            this.mListener.onSearchBack();
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        boolean hasText = s.length() > 0;
        if (this.mClearItem != null) this.mClearItem.setVisible(hasText);
        if (this.mVoiceItem != null) this.mVoiceItem.setVisible(!hasText);

        if (this.mListener != null) {
            this.mListener.onSearchQueryChanged(s.toString().trim());
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        submit();
        return true;
    }

    private void submit() {
        String query = this.mQueryView.getText().toString().trim();
        if (query.isEmpty()) return;

        if (this.mListener != null) {
            this.mListener.onSearchSubmitted(query);
        }
        deactivate();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            activate();
        } else if (id == R.id.clear_history) {
            if (this.mListener != null) this.mListener.onSearchClearHistoryClicked();
        } else if (id == R.id.voice) {
            if (this.mListener != null) this.mListener.onSearchVoiceClicked();
        }

        if (this.mExtraMenuListener != null) {
            this.mExtraMenuListener.onMenuItemClick(item);
        }
        return true;
    }
}
