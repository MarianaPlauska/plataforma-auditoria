"use client";

import { createTheme, rem } from "@mantine/core";

export const theme = createTheme({
  primaryColor: "blue",
  defaultRadius: "md",
  fontFamily: "Segoe UI, system-ui, -apple-system, sans-serif",
  headings: {
    fontFamily: "Segoe UI, system-ui, -apple-system, sans-serif",
    fontWeight: "600",
  },
  fontSizes: {
    xs: rem(12),
    sm: rem(14),
    md: rem(16),
    lg: rem(18),
    xl: rem(20),
  },
  lineHeights: {
    xs: "1.45",
    sm: "1.5",
    md: "1.55",
    lg: "1.6",
    xl: "1.65",
  },
  colors: {
    blue: [
      "#eef4fb",
      "#dce8f5",
      "#b9d1ea",
      "#94b9de",
      "#6fa2d2",
      "#4f8ec7",
      "#3d7fbd",
      "#336da7",
      "#2c6094",
      "#245281",
    ],
  },
  other: {
    bodyBackground: "var(--mantine-color-gray-0)",
  },
});
