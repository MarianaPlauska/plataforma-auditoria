import type { Metadata } from "next";
import "@mantine/core/styles.css";
import "@mantine/dropzone/styles.css";
import { ColorSchemeScript } from "@mantine/core";
import { AppProviders } from "@/components/AppProviders";

export const metadata: Metadata = {
  title: "Audit Desk | Governança e auditoria",
  description: "Workspace de ingestão documental e auditoria assistida por IA.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR">
      <head>
        <ColorSchemeScript defaultColorScheme="light" />
      </head>
      <body>
        <AppProviders>{children}</AppProviders>
      </body>
    </html>
  );
}
