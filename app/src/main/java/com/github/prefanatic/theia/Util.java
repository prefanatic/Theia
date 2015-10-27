package com.github.prefanatic.theia;

import android.util.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import boofcv.struct.image.ImageUInt8;
import timber.log.Timber;

public class Util {
    public static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new AreaComparator());
        } else {
            Timber.e("Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static void sobel(ImageUInt8 input) {
        int indexDst = 0;
        byte[] storage = new byte[input.getHeight() * input.getWidth()];

        for (int y = 0; y < input.height; y++) {
            int indexSrc = input.startIndex + y * input.stride;
            for (int x = 0; x < input.width; x++) {
                int value = input.data[indexSrc++];

            }
        }
    }

}
