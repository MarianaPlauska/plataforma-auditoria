import { NextResponse } from "next/server";
import { getSession } from "@/lib/session";
import { getBaseUrl, getLoginUrl } from "@/lib/oidc";

export async function GET(request: Request) {
  try {
    const session = await getSession();
    session.destroy();
    const loginUrl = await getLoginUrl(request, session);
    await session.save();
    return NextResponse.redirect(loginUrl);
  } catch {
    return NextResponse.redirect(new URL("/login?error=oidc", getBaseUrl(request)));
  }
}
