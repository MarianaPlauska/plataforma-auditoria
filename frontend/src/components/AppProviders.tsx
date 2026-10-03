"use client";

import { MantineProvider } from "@mantine/core";
import { AuthProvider } from "@/components/AuthProvider";
import { theme } from "@/theme";

export function AppProviders({ children }: { children: React.ReactNode }) {
  return (
    <MantineProvider theme={theme} defaultColorScheme="light">
      <AuthProvider>{children}</AuthProvider>
    </MantineProvider>
  );
}
