import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Audit Desk | Governance Agent",
  description: "Workspace de ingestão documental e auditoria assistida por IA.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="pt-BR"><body>{children}</body></html>;
}
