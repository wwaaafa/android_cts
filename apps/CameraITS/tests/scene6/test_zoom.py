# Copyright 2020 The Android Open Source Project
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
"""Verify zoom ratio scales circle sizes correctly."""


import logging
import os.path

import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_base_test
import its_session_utils
from mobly import test_runner
import numpy as np
import opencv_processing_utils
import zoom_capture_utils

_CIRCLE_COLOR = 0  # [0: black, 255: white]
_CIRCLE_AR_RTOL = 0.15  # contour width vs height (aspect ratio)
_CIRCLISH_RTOL = 0.05  # contour area vs ideal circle area pi*((w+h)/4)**2
_JPEG_STR = 'jpg'
_MIN_AREA_RATIO = 0.00015  # based on 2000/(4000x3000) pixels
_MIN_CIRCLE_PTS = 25
_MIN_FOCUS_DIST_TOL = 0.80  # allow charts a little closer than min
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 10
_TEST_FORMATS = ['yuv']  # list so can be appended for newer Android versions
_ZOOM_MIN_THRESH = 2.0


class ZoomTest(its_base_test.ItsBaseTest):
  """Test the camera zoom behavior.
  """

  def test_zoom(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.zoom_ratio_range(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      z_range = props['android.control.zoomRatioRange']
      logging.debug('testing zoomRatioRange: %s', str(z_range))
      debug = self.debug_mode

      z_min, z_max = float(z_range[0]), float(z_range[1])
      camera_properties_utils.skip_unless(z_max >= z_min * _ZOOM_MIN_THRESH)
      z_list = np.arange(z_min, z_max, float(z_max - z_min) / (_NUM_STEPS - 1))
      z_list = np.append(z_list, z_max)

      # set TOLs based on camera and test rig params
      if camera_properties_utils.logical_multi_camera(props):
        test_tols, size = zoom_capture_utils.get_test_tols_and_cap_size(
            cam, props, self.chart_distance, debug)
      else:
        test_tols = {}
        fls = props['android.lens.info.availableFocalLengths']
        for fl in fls:
          test_tols[fl] = (zoom_capture_utils.RADIUS_RTOL,
                           zoom_capture_utils.OFFSET_RTOL)
        yuv_size = capture_request_utils.get_largest_yuv_format(props)
        size = [yuv_size['width'], yuv_size['height']]
      logging.debug('capture size: %s', str(size))
      logging.debug('test TOLs: %s', str(test_tols))

      # determine vendor API level and test_formats to test
      test_formats = _TEST_FORMATS
      vendor_api_level = its_session_utils.get_vendor_api_level(self.dut.serial)
      if vendor_api_level >= its_session_utils.ANDROID14_API_LEVEL:
        test_formats.append(_JPEG_STR)

      # do captures over zoom range and find circles with cv2
      img_name_stem = f'{os.path.join(self.log_path, _NAME)}'
      if camera_properties_utils.manual_sensor(props):
        logging.debug('Manual sensor, using manual capture request')
        s, e, _, _, f_d = cam.do_3a(get_results=True)
        req = capture_request_utils.manual_capture_request(
            s, e, f_distance=f_d)
      else:
        logging.debug('Using auto capture request')
        cam.do_3a()
        req = capture_request_utils.auto_capture_request()
      test_failed = False
      for fmt in test_formats:
        logging.debug('testing %s format', fmt)
        test_data = {}
        for i, z in enumerate(z_list):
          req['android.control.zoomRatio'] = z
          cap = cam.do_capture(
              req, {'format': fmt, 'width': size[0], 'height': size[1]})

          img = image_processing_utils.convert_capture_to_rgb_image(
              cap, props=props)
          img_name = f'{img_name_stem}_{fmt}_{round(z, 2)}.{_JPEG_STR}'
          image_processing_utils.write_image(img, img_name)

          # determine radius tolerance of capture
          cap_fl = cap['metadata']['android.lens.focalLength']
          radius_tol, offset_tol = test_tols[cap_fl]

          # convert [0, 1] image to [0, 255] and cast as uint8
          img = image_processing_utils.convert_image_to_uint8(img)

          # Find the center circle in img
          try:
            circle = opencv_processing_utils.find_center_circle(
                img, img_name, _CIRCLE_COLOR, circle_ar_rtol=_CIRCLE_AR_RTOL,
                circlish_rtol=_CIRCLISH_RTOL,
                min_area=_MIN_AREA_RATIO * size[0] * size[1] * z * z,
                min_circle_pts=_MIN_CIRCLE_PTS, debug=debug)
            if opencv_processing_utils.is_circle_cropped(circle, size):
              logging.debug('zoom %.2f is too large! Skip further captures', z)
              break
          except AssertionError as e:
            if z/z_list[0] >= zoom_capture_utils.ZOOM_MAX_THRESH:
              break
            else:
              raise AssertionError(
                  'No circle detected for zoom ratio <= '
                  f'{zoom_capture_utils.ZOOM_MAX_THRESH}. '
                  'Take pictures according to instructions carefully!') from e
          test_data[i] = {'z': z, 'circle': circle, 'r_tol': radius_tol,
                          'o_tol': offset_tol, 'fl': cap_fl}

          test_failed_for_ratio = zoom_capture_utils.verify_zoom_results(
              test_data, size, z_max, z_min)
          if test_failed_for_ratio:
            test_failed = True

    if test_failed:
      raise AssertionError(f'{_NAME} failed! Check test_log.DEBUG for errors')

if __name__ == '__main__':
  test_runner.main()
