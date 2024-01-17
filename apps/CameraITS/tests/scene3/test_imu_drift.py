# Copyright 2014 The Android Open Source Project
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
"""Verify IMU has stable output when device is stationary."""

import logging
import math
import os

import matplotlib
from matplotlib import pylab
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import its_session_utils

_RAD_TO_DEG = 180/math.pi
_GYRO_DRIFT_THRESH = 0.01*_RAD_TO_DEG  # PASS/FAIL for gyro accumulated drift
_GYRO_MEAN_THRESH = 0.01*_RAD_TO_DEG  # PASS/FAIL for gyro mean drift
_GYRO_VAR_ATOL = 1E-7  # rad^2/sec^2/Hz from CDD C-1-7
_IMU_EVENTS_WAIT_TIME = 30  # seconds
_NAME = os.path.basename(__file__).split('.')[0]
_NSEC_TO_SEC = 1E-9
_RV_DRIFT_THRESH = 0.01*_RAD_TO_DEG  # PASS/FAIL for rotation vector drift


def calc_effective_sampling_rate(times, sensor):
  """Calculate the effective sampling rate for gyro & RV.

  Args:
    times: array/list of times
    sensor: string of sensor type

  Returns:
    effective_sampling_rate
  """
  duration = times[-1] - times[0]
  num_pts = len(times)
  logging.debug('%s time: %.2fs, num_pts: %d, effective samping rate: %.2f Hz',
                sensor, duration, num_pts, num_pts/duration)
  return num_pts/duration


def define_3axis_plot(x, y, z, t, plot_name):
  """Define common 3-axis plot figure, data, and title with RGB coloring.

  Args:
    x: list of x values
    y: list of y values
    z: list of z values
    t: list of time values for x, y, z data
    plot_name: str name of plot and figure
  """
  pylab.figure(plot_name)
  pylab.plot(t, x, 'r', label='x')
  pylab.plot(t, y, 'g', label='y')
  pylab.plot(t, z, 'b', label='z')
  pylab.xlabel('Time (seconds)')
  pylab.title(plot_name)
  pylab.legend()


def plot_rotation_vector_data(x, y, z, t, log_path):
  """Plot raw gyroscope output data.

  Args:
    x: list of rotation vector x values
    y: list of rotation vector y values
    z: list of rotation vector z values
    t: list of time values for x, y, z data
    log_path: str of location for output path
  """
  # normalize to initial position
  x = x - x[0]
  y = y - y[0]
  z = z - z[0]

  plot_name = f'{_NAME}_rotation_vector'
  define_3axis_plot(x, y, z, t, plot_name)
  pylab.ylabel('Drift (degrees)')
  pylab.ylim([min([np.amin(x), np.amin(y), np.amin(z), -_RV_DRIFT_THRESH]),
              max([np.amax(x), np.amax(y), np.amax(x), _RV_DRIFT_THRESH])])
  matplotlib.pyplot.savefig(f'{os.path.join(log_path, plot_name)}.png')


def plot_raw_gyro_data(x, y, z, t, log_path):
  """Plot raw gyroscope output data.

  Args:
    x: list of x values
    y: list of y values
    z: list of z values
    t: list of time values for x, y, z data
    log_path: str of location for output path
  """

  plot_name = f'{_NAME}_gyro_raw'
  define_3axis_plot(x, y, z, t, plot_name)
  pylab.ylabel('Gyro raw output (degrees)')
  pylab.ylim([min([np.amin(x), np.amin(y), np.amin(z), -_GYRO_MEAN_THRESH]),
              max([np.amax(x), np.amax(y), np.amax(x), _GYRO_MEAN_THRESH])])
  matplotlib.pyplot.savefig(f'{os.path.join(log_path, plot_name)}.png')


def do_riemann_sums(x, y, z, t, log_path):
  """Do integration estimation using Riemann sums and plot.

  Args:
    x: list of x values
    y: list of y values
    z: list of z values
    t: list of time values for x, y, z data
    log_path: str of location for output path
  """
  x_int, y_int, z_int = 0, 0, 0
  x_sums, y_sums, z_sums = [0], [0], [0]
  for i in range(len(t)):
    if i > 0:
      x_int += x[i] * (t[i] - t[i-1])
      y_int += y[i] * (t[i] - t[i-1])
      z_int += z[i] * (t[i] - t[i-1])
      x_sums.append(x_int)
      y_sums.append(y_int)
      z_sums.append(z_int)

  # find min/maxes
  x_min, x_max = min(x_sums), max(x_sums)
  y_min, y_max = min(y_sums), max(y_sums)
  z_min, z_max = min(z_sums), max(z_sums)
  logging.debug('Integrated drift min/max (degrees) in %d seconds, '
                'x: %.3f/%.3f, y: %.3f/%.3f, z: %.3f/%.3f',
                _IMU_EVENTS_WAIT_TIME, x_min, x_max, y_min, y_max, z_min, z_max)

  # plot accumulated gyro drift
  plot_name = f'{_NAME}_gyro_drift'
  define_3axis_plot(x_sums, y_sums, z_sums, t, plot_name)
  pylab.ylabel('Drift (degrees)')
  pylab.ylim([min([x_min, y_min, z_min, -_GYRO_DRIFT_THRESH]),
              max([x_max, y_max, z_max, _GYRO_DRIFT_THRESH])])
  matplotlib.pyplot.savefig(f'{os.path.join(log_path, plot_name)}.png')


def convert_events_to_arrays(events, t_factor, xyz_factor):
  """Convert data from get_sensor_events() into x, y, z, t.

  Args:
    events: dict from cam.get_sensor_events()
    t_factor: time multiplication factor ie. NSEC_TO_SEC
    xyz_factor: xyz multiplicaiton factor ie. RAD_TO_DEG

  Returns:
    x, y, z, t numpy arrays
  """
  t = np.array([(e['time'] - events[0]['time'])*t_factor
                for e in events])
  x = np.array([e['x']*xyz_factor for e in events])
  y = np.array([e['y']*xyz_factor for e in events])
  z = np.array([e['z']*xyz_factor for e in events])

  return x, y, z, t


class ImuDriftTest(its_base_test.ItsBaseTest):
  """Test if the IMU has stable output when device is stationary."""

  def test_imu_drift(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.sensor_fusion(props) and
          cam.get_sensors().get('gyro'))

      # load scene
      its_session_utils.load_scene(cam, props, self.scene,
                                   self.tablet, self.chart_distance)

      # determine preview size
      supported_preview_sizes = cam.get_supported_preview_sizes(self.camera_id)
      logging.debug('Supported preview resolutions: %s',
                    supported_preview_sizes)
      preview_size = supported_preview_sizes[-1]
      logging.debug('Tested preview resolution: %s', preview_size)

      # start collecting gyro events
      logging.debug('Collecting gyro events')
      cam.start_sensor_events()

      # do preview recording
      cam.do_preview_recording(
          video_size=preview_size, duration=_IMU_EVENTS_WAIT_TIME,
          stabilize=True)

      # dump IMU events
      sensor_events = cam.get_sensor_events()
      gyro_events = sensor_events['gyro']  # raw gyro output
      rv_events = sensor_events['rv']  # rotation vector

    # process gyro data
    x_gyro, y_gyro, z_gyro, times = convert_events_to_arrays(
        gyro_events, _NSEC_TO_SEC, _RAD_TO_DEG)
    # gyro sampling rate is SENSOR_DELAY_FASTEST in ItsService.java
    gyro_sampling_rate = calc_effective_sampling_rate(times, 'gyro')

    plot_raw_gyro_data(x_gyro, y_gyro, z_gyro, times, self.log_path)
    do_riemann_sums(x_gyro, y_gyro, z_gyro, times, self.log_path)

    # process rotation vector data
    x_rv, y_rv, z_rv, t_rv = convert_events_to_arrays(
        rv_events, _NSEC_TO_SEC, 1)
    # Rotation Vector sampling rate is SENSOR_DELAY_FASTEST in ItsService.java
    calc_effective_sampling_rate(t_rv, 'RV')

    # plot rotation vector data
    plot_rotation_vector_data(x_rv, y_rv, z_rv, t_rv, self.log_path)

    # assert correct gyro behavior
    gyro_var_atol = _GYRO_VAR_ATOL * gyro_sampling_rate * _RAD_TO_DEG**2
    for i, samples in enumerate([x_gyro, y_gyro, z_gyro]):
      gyro_mean = samples.mean()
      gyro_var = np.var(samples)
      logging.debug('%s gyro_mean: %.3e', 'XYZ'[i], gyro_mean)
      logging.debug('%s gyro_var: %.3e', 'XYZ'[i], gyro_var)
      if gyro_mean >= _GYRO_MEAN_THRESH:
        raise AssertionError(f'gyro_mean: {gyro_mean}.3e, '
                             f'TOL={_GYRO_MEAN_THRESH}')
      if gyro_var >= gyro_var_atol:
        raise AssertionError(f'gyro_var: {gyro_var}.3e, '
                             f'ATOL={gyro_var_atol}.3e')


if __name__ == '__main__':
  test_runner.main()
