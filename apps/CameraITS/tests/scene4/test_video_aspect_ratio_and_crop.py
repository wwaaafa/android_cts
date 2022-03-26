# Copyright 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Validate video aspect ratio, crop and FoV vs format."""

import logging
import os.path
from mobly import test_runner

import its_base_test
import camera_properties_utils
import its_session_utils
import video_processing_utils

_ANDROID13_API_LEVEL = 32
_NAME = os.path.splitext(os.path.basename(__file__))[0]


class VideoAspectRatioAndCropTest(its_base_test.ItsBaseTest):
  """Test aspect ratio/field of view/cropping for each tested fmt.

    This test checks for:
    1. Aspect ratio: images are not stretched
    2. Crop: center of images is not shifted
    3. FOV: images cropped to keep maximum possible FOV with only 1 dimension
       (horizontal or veritical) cropped.

  The test video is a black circle on a white background.

  When RAW capture is available, set the height vs. width ratio of the circle in
  the full-frame RAW as ground truth. In an ideal setup such ratio should be
  very close to 1.0, but here we just use the value derived from full resolution
  RAW as ground truth to account for the possibility that the chart is not well
  positioned to be precisely parallel to image sensor plane.
  The test then compares the ground truth ratio with the same ratio measured
  on videos captued using different formats.

  If RAW capture is unavailable, a full resolution JPEG image is used to setup
  ground truth. In this case, the ground truth aspect ratio is defined as 1.0
  and it is the tester's responsibility to make sure the test chart is
  properly positioned so the detected circles indeed have aspect ratio close
  to 1.0 assuming no bugs causing image stretched.

  The aspect ratio test checks the aspect ratio of the detected circle and
  it will fail if the aspect ratio differs too much from the ground truth
  aspect ratio mentioned above.

  The FOV test examines the ratio between the detected circle area and the
  image size. When the aspect ratio of the test image is the same as the
  ground truth image, the ratio should be very close to the ground truth
  value. When the aspect ratio is different, the difference is factored in
  per the expectation of the Camera2 API specification, which mandates the
  FOV reduction from full sensor area must only occur in one dimension:
  horizontally or vertically, and never both. For example, let's say a sensor
  has a 16:10 full sensor FOV. For all 16:10 output images there should be no
  FOV reduction on them. For 16:9 output images the FOV should be vertically
  cropped by 9/10. For 4:3 output images the FOV should be cropped
  horizontally instead and the ratio (r) can be calculated as follows:
      (16 * r) / 10 = 4 / 3 => r = 40 / 48 = 0.8333
  Say the circle is covering x percent of the 16:10 sensor on the full 16:10
  FOV, and assume the circle in the center will never be cut in any output
  sizes (this can be achieved by picking the right size and position of the
  test circle), the from above cropping expectation we can derive on a 16:9
  output image the circle will cover (x / 0.9) percent of the 16:9 image; on
  a 4:3 output image the circle will cover (x / 0.8333) percent of the 4:3
  image.

  The crop test checks that the center of any output image remains aligned
  with center of sensor's active area, no matter what kind of cropping or
  scaling is applied. The test verifies that by checking the relative vector
  from the image center to the center of detected circle remains unchanged.
  The relative part is normalized by the detected circle size to account for
  scaling effect.
  """

  def test_video_aspect_ratio_and_crop(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      fls_logical = props['android.lens.info.availableFocalLengths']
      logging.debug('logical available focal lengths: %s', str(fls_logical))
      props = cam.override_with_hidden_physical_camera_props(props)
      fls_physical = props['android.lens.info.availableFocalLengths']
      logging.debug('physical available focal lengths: %s', str(fls_physical))
      # Check SKIP conditions.
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= _ANDROID13_API_LEVEL)
      its_session_utils.load_scene(cam, props, self.scene,
                                   self.tablet, chart_distance=0)
      supported_video_qualities = cam.get_supported_video_qualities(
          self.camera_id)
      test_quality_list = video_processing_utils.create_test_format_list(
          supported_video_qualities)
      logging.debug('Video qualities to be tested: %s', test_quality_list)
      # TODO(ruchamk):Add video recordin, aspect ratio and crop checks.

if __name__ == '__main__':
  test_runner.main()
