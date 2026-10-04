"use client";

import { useEffect, useState } from "react";
import { Alert, Box, Button, Center, Container, Paper, Stack, Text, Title } from "@mantine/core";
import { IconLock } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthProvider";

export default function LoginPage() {
  const router = useRouter();
  const { session, ready } = useAuth();
  const [error, setError] = useState("");

  useEffect(() => {
    if (ready && session) router.replace("/");
    const code = new URLSearchParams(window.location.search).get("error");
    if (code) setError("Não foi possível concluir a autenticação. Tente novamente ou contate o administrador.");
  }, [ready, session, router]);

  return (
    <Center mih="100vh" bg="gray.0">
      <Container size={440} w="100%">
        <Paper withBorder radius="md" p="xl" shadow="sm">
          <Stack gap="lg">
            {error && <Alert color="red" role="alert">{error}</Alert>}
            <Stack gap="xs" align="center" ta="center">
              <Box w={48} h={48} bg="blue.6" c="white" style={{ borderRadius: 12, display: "grid", placeItems: "center" }} fw={700}>
                EA
              </Box>
              <Title order={2} fw={600}>Audit Desk</Title>
              <Text c="dimmed" size="sm">
                Entre com sua conta corporativa para acessar o workspace de auditoria.
              </Text>
            </Stack>

            <Button component="a" href="/api/auth/login" fullWidth leftSection={<IconLock size={16} />}>
              Entrar com conta corporativa
            </Button>

            <Text size="xs" c="dimmed" ta="center">
              A autenticação será concluída no provedor de identidade da organização.
            </Text>
          </Stack>
        </Paper>
      </Container>
    </Center>
  );
}
