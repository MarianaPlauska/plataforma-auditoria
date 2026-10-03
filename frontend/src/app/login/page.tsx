"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Center,
  Container,
  Paper,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { IconAlertCircle, IconLock } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthProvider";

export default function LoginPage() {
  const router = useRouter();
  const { session, login, ready } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (ready && session) router.replace("/");
  }, [ready, session, router]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      const data = await response.json() as { accessToken?: string; error?: string };
      if (!response.ok || !data.accessToken) {
        throw new Error(data.error ?? "Não foi possível entrar.");
      }
      login(data.accessToken);
      router.replace("/");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível entrar.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Center mih="100vh" bg="gray.0">
      <Container size={420} w="100%">
        <Paper withBorder radius="md" p="xl" shadow="sm">
          <Stack gap="lg">
            <Stack gap="xs" align="center" ta="center">
              <Box w={48} h={48} bg="blue.6" c="white" style={{ borderRadius: 12, display: "grid", placeItems: "center" }} fw={700}>
                EA
              </Box>
              <Title order={2} fw={600}>Audit Desk</Title>
              <Text c="dimmed" size="sm">
                Entre com sua conta corporativa para acessar o workspace de auditoria.
              </Text>
            </Stack>

            {error && <Alert color="red" icon={<IconAlertCircle size={16} />}>{error}</Alert>}

            <form onSubmit={handleSubmit}>
              <Stack gap="md">
                <TextInput
                  label="E-mail"
                  type="email"
                  autoComplete="username"
                  placeholder="seu.email@empresa.com"
                  value={email}
                  onChange={(event) => setEmail(event.currentTarget.value)}
                  required
                />
                <PasswordInput
                  label="Senha"
                  autoComplete="current-password"
                  placeholder="Sua senha"
                  value={password}
                  onChange={(event) => setPassword(event.currentTarget.value)}
                  required
                />
                <Button type="submit" fullWidth leftSection={<IconLock size={16} />} loading={loading}>
                  Entrar
                </Button>
              </Stack>
            </form>

            <Text size="xs" c="dimmed" ta="center">
              Ambiente local: use <strong>auditor@local.demo</strong> / <strong>demo123</strong> após subir o Keycloak.
            </Text>
          </Stack>
        </Paper>
      </Container>
    </Center>
  );
}
