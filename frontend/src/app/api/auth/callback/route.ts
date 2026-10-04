import { NextResponse } from "next/server";
import { finishLogin, getBaseUrl } from "@/lib/oidc";
import { getSession } from "@/lib/session";

export async function GET(request: Request) {
  const session = await getSession();
  try {
    await finishLogin(request, session);
    await session.save();
    return NextResponse.redirect(new URL("/", getBaseUrl(request)));
  } catch {
    session.destroy();
    return NextResponse.redirect(new URL("/login?error=callback", getBaseUrl(request)));
  }
}
