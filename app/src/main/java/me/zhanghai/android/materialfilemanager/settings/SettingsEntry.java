/*
 * Copyright (c) 2018 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.materialfilemanager.settings;

import android.support.annotation.StringRes;

import me.zhanghai.android.materialfilemanager.AppApplication;
import me.zhanghai.android.materialfilemanager.util.SharedPrefsUtils;

public abstract class SettingsEntry<T> implements SharedPrefsUtils.Entry<T> {

    private int mKeyResId;
    private int mDefaultValueResId;

    public SettingsEntry(@StringRes int keyResId, int defaultValueResId) {
        mKeyResId = keyResId;
        mDefaultValueResId = defaultValueResId;
    }

    @Override
    public String getKey() {
        return AppApplication.getInstance().getString(mKeyResId);
    }

    protected int getDefaultValueResId() {
        return mDefaultValueResId;
    }

    public abstract T getValue();

    public abstract void putValue(T value);

    public void remove() {
        SharedPrefsUtils.remove(this);
    }
}