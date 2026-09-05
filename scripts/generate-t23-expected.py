#!/usr/bin/env python3
"""Regenerate the analytical T23 raster; offline authoring uses Pillow 10.2.0.

The fixture is project-authored Apache-2.0 data, not renderer-derived imagery.
At 144 DPI each PDF point is two pixels. Left half: red page content.
Upper right quarter: existing green annotation appearance. Remainder: white.
"""
from pathlib import Path
from PIL import Image, ImageDraw

image = Image.new("RGB", (1224, 1584), "white")
draw = ImageDraw.Draw(image)
draw.rectangle((0, 0, 611, 1583), fill=(255, 0, 0))
draw.rectangle((612, 0, 1223, 791), fill=(0, 255, 0))
image.save(Path(__file__).resolve().parent.parent / "capabilities/expected/T23-page-rendering-144dpi-srgb.png")
