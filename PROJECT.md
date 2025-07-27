# LIDAR Localization Project Context

This document provides context for AI agents working on indoor localization using 2D LiDAR (LD06) and GeoJSON-defined floor maps.

## Goal

Estimate the (x, y) position of a mobile device using LD06 LiDAR scan data matched against a known 2D map. Each estimate should include a timestamp and a confidence value.

## System Overview

- Input:
  - 2D LiDAR scan data (angles and distances) from LD06
  - Static environment map in GeoJSON Polygon format
  - Optional: orientation or motion data from IMU

- Output:
  - (x, y) coordinates in the GeoJSON map's frame
  - Timestamp of the estimate
  - Confidence score (0 to 1)

- Update frequency: target 1 Hz; acceptable minimum 1 per 5 seconds

## Project Phases

1. Iteration 1: Basic Localization
   - Single, empty room
   - Wall distance matching via simple geometry
   - Assumes known orientation and unobstructed view

2. Iteration 2: Robust Indoor Positioning
   - Real-world conditions (occlusion, noise, unknown pose)
   - Monte Carlo Localization with particle filter
   - Motion estimation using device IMU

3. Iteration 3: Full-Floor Navigation
   - Large-scale floor maps with multiple rooms and hallways
   - Global localization and continuous tracking across spaces
   - Map-aware route following and pose estimation

## Technical Notes

- LD06 provides ~10 Hz scans, up to 360 degrees, ~450 measurements per rotation, 12m range
- One side of the scan is typically blocked by the user
- GeoJSON coordinates are treated as meters in a local map frame, no origin assumptions
- Position output should be compatible with mobile platforms

## Related Files

- Sample geojsons at `samples/*.geojson.json`
- Sample lidar sessions samples at `samples/*.session.json`
- Sample files might be zipped `*.zip`
