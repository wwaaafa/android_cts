/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.mediaediting.cts;

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;

import java.io.IOException;

/** Utilities for Media Editing tests. */
public final class MediaEditingUtil {

  public static final String MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_1920W_1080H_1S_URI_STRING =
    "sample_with_increasing_timestamps_1920x1080_30fps_avc.mp4";

  /** Baseline profile level 3.0 H.264 stream, which should be supported on all devices. */
  public static final String MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_320W_240H_5S_URI_STRING =
    "sample_with_increasing_timestamps_320x240_30fps_avc.mp4";

  public static final String MP4_ASSET_H264_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING =
    "bbb_642x642_1mbps_30fps_avc.mp4";

  public static final String MKV_ASSET_H264_340W_280H_10BIT =
      "cosmat_340x280_24fps_crf22_avc_10bit.mkv";

  public static final String MKV_ASSET_H264_520W_390H_10BIT =
      "cosmat_520x390_24fps_crf22_avc_10bit.mkv";

  public static final String MKV_ASSET_H264_640W_360H_10BIT =
      "cosmat_640x360_24fps_crf22_avc_10bit_nob.mkv";

  public static final String MKV_ASSET_H264_800W_640H_10BIT =
      "cosmat_800x640_24fps_crf22_avc_10bit_nob.mkv";

  public static final String MKV_ASSET_H264_1280W_720H_10BIT =
      "cosmat_1280x720_24fps_crf22_avc_10bit_nob.mkv";

  public static final String MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_1920_1080_1S_URI_STRING =
    "bbb_1920x1080_30fps_hevc_main_l40.mp4";

  public static final String MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_720W_480H_1S_URI_STRING =
    "bbb_720x480_30fps_hevc_main_l3.mp4";

  public static final String MP4_ASSET_HEVC_WITH_INCREASING_TIMESTAMPS_642W_642H_3S_URI_STRING =
    "bbb_642x642_768kbps_30fps_hevc.mp4";

  public static final String MKV_ASSET_HEVC_340W_280H_5S_10BIT =
      "cosmat_340x280_24fps_crf22_hevc_10bit.mkv";

  public static final String MKV_ASSET_HEVC_520W_390H_5S_10BIT =
      "cosmat_520x390_24fps_crf22_hevc_10bit.mkv";

  public static final String MKV_ASSET_HEVC_640W_360H_5S_10BIT =
      "cosmat_640x360_24fps_crf22_hevc_10bit_nob.mkv";

  public static final String MKV_ASSET_HEVC_800W_640H_5S_10BIT =
      "cosmat_800x640_24fps_crf22_hevc_10bit_nob.mkv";

  public static final String MKV_ASSET_HEVC_1280W_720H_5S_10BIT =
      "cosmat_1280x720_24fps_crf22_hevc_10bit_nob.mkv";

  public static Format getMuxedWidthHeight(String filePath) throws IOException {
    MediaExtractor mediaExtractor = new MediaExtractor();
    mediaExtractor.setDataSource(filePath);
    @Nullable MediaFormat mediaFormat = null;
    for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
      if (MimeTypes.isVideo(mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME))) {
        mediaFormat = mediaExtractor.getTrackFormat(i);
        mediaExtractor.selectTrack(i);
        break;
      }
    }

    checkStateNotNull(mediaFormat);
    checkState(mediaFormat.containsKey(MediaFormat.KEY_WIDTH));
    checkState(mediaFormat.containsKey(MediaFormat.KEY_HEIGHT));

    int rotationDegree = 0;
    if (mediaFormat.containsKey(MediaFormat.KEY_ROTATION)) {
      rotationDegree = mediaFormat.getInteger(MediaFormat.KEY_ROTATION);
    }
    return new Format.Builder()
        .setWidth(mediaFormat.getInteger(MediaFormat.KEY_WIDTH))
        .setHeight(mediaFormat.getInteger(MediaFormat.KEY_HEIGHT))
        .setRotationDegrees(rotationDegree)
        .build();
  }

  public static int getMuxedOutputProfile(String filePath) throws IOException {
    MediaExtractor mediaExtractor = new MediaExtractor();
    mediaExtractor.setDataSource(filePath);
    @Nullable MediaFormat mediaFormat = null;
    for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
      if (MimeTypes.isVideo(mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME))) {
        mediaFormat = mediaExtractor.getTrackFormat(i);
        mediaExtractor.selectTrack(i);
        break;
      }
    }

    checkStateNotNull(mediaFormat);
    checkState(mediaFormat.containsKey(MediaFormat.KEY_PROFILE));
    return mediaFormat.getInteger(MediaFormat.KEY_PROFILE, -1);
  }
}
