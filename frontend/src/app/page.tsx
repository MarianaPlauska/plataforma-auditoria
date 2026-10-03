"use client";

import { useEffect } from "react";
import { Center, Loader } from "@mantine/core";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthProvider";
import { Dashboard } from "@/components/Dashboard";

export default function Home() {
  const { session, ready } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (ready && !session) router.replace("/login");
  }, [ready, session, router]);

  if (!ready || !session) {
    return (
      <Center mih="100vh">
        <Loader />
      </Center>
    );
  }

  return <Dashboard />;
}
