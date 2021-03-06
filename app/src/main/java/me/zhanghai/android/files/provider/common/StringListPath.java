/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common;

import android.os.Parcel;
import android.os.Parcelable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java8.nio.file.InvalidPathException;
import java8.nio.file.Path;
import java8.nio.file.ProviderMismatchException;
import me.zhanghai.android.files.util.CollectionUtils;

public abstract class StringListPath extends AbstractPath implements Parcelable {

    private final char mSeparator;

    private final boolean mAbsolute;

    @NonNull
    private final List<String> mNames;

    public StringListPath(char separator, @NonNull String path) {
        Objects.requireNonNull(path);
        if (separator == '\0') {
            throw new IllegalArgumentException("Separator cannot be a nul character");
        }
        mSeparator = separator;
        requireNoNulCharacter(path);
        List<String> names = new ArrayList<>();
        if (path.isEmpty()) {
            names.add("");
        } else {
            for (int start = 0, end, length = path.length(); start < length; ) {
                while (start < length && path.charAt(start) == mSeparator) {
                    ++start;
                }
                if (start == length) {
                    break;
                }
                end = start + 1;
                while (end < length && path.charAt(end) != mSeparator) {
                    ++end;
                }
                names.add(path.substring(start, end));
                start = end;
            }
        }
        mNames = Collections.unmodifiableList(names);
        mAbsolute = isPathAbsolute(path);
        requireNameIfNonAbsolute();
    }

    private static void requireNoNulCharacter(@NonNull String input) {
        for (int i = 0, length = input.length(); i < length; ++i) {
            if (input.charAt(i) == '\0') {
                throw new InvalidPathException(input, "Path cannot contain nul character", i);
            }
        }
    }

    protected StringListPath(char separator, boolean absolute, @NonNull List<String> names) {
        mSeparator = separator;
        mAbsolute = absolute;
        mNames = Collections.unmodifiableList(names);
        requireNameIfNonAbsolute();
    }

    private void requireNameIfNonAbsolute() {
        if (!mAbsolute && mNames.isEmpty()) {
            throw new AssertionError("Non-absolute path must have at least one name");
        }
    }

    protected final char getSeparator() {
        return mSeparator;
    }

    @Override
    public final boolean isAbsolute() {
        return mAbsolute;
    }

    @Override
    public final int getNameCount() {
        return mNames.size();
    }

    @NonNull
    @Override
    public final Path getName(int index) {
        if (index < 0 || index >= mNames.size()) {
            throw new IllegalArgumentException();
        }
        return createPath(false, Collections.singletonList(mNames.get(index)));
    }

    @NonNull
    @Override
    public Path subpath(int beginIndex, int endIndex) {
        int namesSize = mNames.size();
        if (beginIndex < 0 || beginIndex >= namesSize || endIndex <= beginIndex
                || endIndex > namesSize) {
            throw new IllegalArgumentException();
        }
        List<String> subNames = new ArrayList<>(mNames.subList(beginIndex, endIndex));
        return createPath(false, subNames);
    }

    @Override
    public boolean startsWith(@NonNull Path other) {
        Objects.requireNonNull(other);
        if (other == this) {
            return true;
        }
        if (other.getClass() != getClass()) {
            return false;
        }
        StringListPath otherPath = (StringListPath) other;
        return CollectionUtils.startsWith(mNames, otherPath.mNames);
    }

    @Override
    public boolean endsWith(@NonNull Path other) {
        Objects.requireNonNull(other);
        if (other == this) {
            return true;
        }
        if (other.getClass() != getClass()) {
            return false;
        }
        StringListPath otherPath = (StringListPath) other;
        return CollectionUtils.endsWith(mNames, otherPath.mNames);
    }

    @NonNull
    @Override
    public Path normalize() {
        List<String> normalizedNames = new ArrayList<>();
        for (String name : mNames) {
            switch (name) {
                case ".":
                    break;
                case "..": {
                    int normalizedNamesSize = normalizedNames.size();
                    if (normalizedNamesSize == 0) {
                        if (!mAbsolute) {
                            normalizedNames.add(name);
                        }
                    } else {
                        int normalizedNamesLastIndex = normalizedNamesSize - 1;
                        if (Objects.equals(normalizedNames.get(normalizedNamesLastIndex), "..")) {
                            normalizedNames.add(name);
                        } else {
                            normalizedNames.remove(normalizedNamesLastIndex);
                        }
                    }
                    break;
                }
                default:
                    normalizedNames.add(name);
            }
        }
        if (!mAbsolute && normalizedNames.isEmpty()) {
            return createEmptyPath();
        }
        return createPath(mAbsolute, normalizedNames);
    }

    @NonNull
    @Override
    public Path resolve(@NonNull Path other) {
        Objects.requireNonNull(other);
        StringListPath otherPath = toStringListPath(other);
        if (otherPath.mAbsolute) {
            return other;
        }
        if (otherPath.isEmpty()) {
            return this;
        }
        List<String> resolvedNames = new ArrayList<>(CollectionUtils.union(mNames,
                otherPath.mNames));
        return createPath(mAbsolute, resolvedNames);
    }

    @NonNull
    @Override
    public Path relativize(@NonNull Path other) {
        Objects.requireNonNull(other);
        StringListPath otherPath = toStringListPath(other);
        if (otherPath.mAbsolute != mAbsolute) {
            throw new IllegalArgumentException("The other path must be as absolute as this path");
        }
        if (isEmpty()) {
            return otherPath;
        }
        if (equals(otherPath)) {
            return createEmptyPath();
        }
        int namesSize = mNames.size();
        int otherNamesSize = otherPath.mNames.size();
        int lesserNamesSize = Math.min(namesSize, otherNamesSize);
        int commonNamesSize = 0;
        while (commonNamesSize < lesserNamesSize && Objects.equals(mNames.get(commonNamesSize),
                otherPath.mNames.get(commonNamesSize))) {
            ++commonNamesSize;
        }
        List<String> relativeNames = new ArrayList<>();
        int dotDotCount = namesSize - commonNamesSize;
        if (dotDotCount > 0) {
            relativeNames.addAll(Collections.nCopies(dotDotCount, ".."));
        }
        if (commonNamesSize < otherNamesSize) {
            relativeNames.addAll(otherPath.mNames.subList(commonNamesSize, otherNamesSize));
        }
        return createPath(false, relativeNames);
    }

    @NonNull
    @Override
    public URI toUri() {
        String scheme = getFileSystem().provider().getScheme();
        String path = getUriSchemeSpecificPart();
        String fragment = getUriFragment();
        try {
            return new URI(scheme, path, fragment);
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    @NonNull
    @Override
    public Path toAbsolutePath() {
        if (mAbsolute) {
            return this;
        } else {
            return getDefaultDirectory().resolve(this);
        }
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder pathBuilder = new StringBuilder();
        if (mAbsolute) {
            if (getRoot().getNameCount() == 0) {
                pathBuilder.append(mSeparator);
            }
        }
        boolean first = true;
        for (String name : mNames) {
            if (first) {
                first = false;
            } else {
                pathBuilder.append(mSeparator);
            }
            pathBuilder.append(name);
        }
        return pathBuilder.toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        StringListPath that = (StringListPath) object;
        return mSeparator == that.mSeparator
                && mAbsolute == that.mAbsolute
                && Objects.equals(mNames, that.mNames)
                && Objects.equals(getFileSystem(), that.getFileSystem());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSeparator, mAbsolute, mNames, getFileSystem());
    }

    @Override
    public int compareTo(@NonNull Path other) {
        Objects.requireNonNull(other);
        StringListPath otherPath = toStringListPath(other);
        return toString().compareTo(otherPath.toString());
    }

    @NonNull
    private StringListPath toStringListPath(@NonNull Path path) {
        if (path.getClass() != getClass()) {
            throw new ProviderMismatchException(path.toString());
        }
        return (StringListPath) path;
    }

    protected boolean isEmpty() {
        return !mAbsolute && mNames.size() == 1 && Objects.equals(mNames.get(0), "");
    }

    protected abstract boolean isPathAbsolute(@NonNull String path);

    @NonNull
    protected abstract Path createPath(boolean absolute, @NonNull List<String> names);

    @NonNull
    private Path createEmptyPath() {
        return createPath(false, Collections.singletonList(""));
    }

    @Nullable
    protected String getUriSchemeSpecificPart() {
        return toAbsolutePath().toString();
    }

    @Nullable
    protected String getUriFragment() {
        return null;
    }

    @NonNull
    protected abstract Path getDefaultDirectory();


    protected StringListPath(Parcel in) {
        mSeparator = (char) in.readInt();
        mAbsolute = in.readByte() != 0;
        mNames = Collections.unmodifiableList(in.createStringArrayList());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSeparator);
        dest.writeByte(mAbsolute ? (byte) 1 : (byte) 0);
        dest.writeStringList(mNames);
    }
}
