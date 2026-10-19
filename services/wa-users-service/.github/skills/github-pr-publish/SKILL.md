---
name: github-pr-publish
description: >
  Publica en GitHub la rama con el trabajo del equipo y abre el pull request con gh hacia la rama base configurada
  (default develop): verifica gh y el remoto, resuelve la base, hace push de la rama, reutiliza un pull request
  abierto o crea uno con Summary + Test plan, y verifica la base. Agnóstico del stack.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  audience: pr-publisher
---

# Publicar el pull request

Objetivo: **pull request abierto en GitHub** de la rama de la corrida (`head`) hacia la rama base del equipo
(`base`). Push sin pull request = paso **incompleto**.

## 1. Prerrequisitos (en paralelo)

```bash
git status
git branch --show-current
git remote get-url origin
gh auth status
```

- Sin remoto `origin`, sin `gh` o sin sesión → **bloqueado** con el paso exacto que tiene que hacer el usuario
  (`git remote add origin …`, `winget install --id GitHub.cli -e`, `gh auth login`). No lo instales ni inicies
  sesión vos.
- `git status` tiene que estar limpio: el orquestador ya commiteó todo. Si hay cambios sin commitear, **no los
  agregues**: avisalo en el resultado (no son parte de la entrega).

## 2. Rama base (`BASE`)

En este orden (regla `pr-base-branch`):

1. La orden o la especificación de la corrida, si piden una base (ej. "PR contra main").
2. `pr.base` de la regla `pr-base-branch` (default **`develop`**).
3. Si esa rama no existe en `origin`: la rama desde la que se creó la rama de la corrida (el paso te la da).
4. Último recurso: la rama por defecto del repo (`gh repo view --json defaultBranchRef -q .defaultBranchRef.name`).

```bash
git fetch origin
git rev-parse --verify "origin/$BASE"
```

## 3. Publicar la rama de la corrida

```bash
HEAD_BRANCH=$(git branch --show-current)   # la rama de la corrida (ej. opensoma/20261006-ab12)
git log --oneline "origin/$BASE..HEAD"      # tiene que haber commits; si no, no hay nada que proponer
git push -u origin HEAD
```

Si el push falla por non-fast-forward: **no** force push → bloqueado con el motivo.

## 4. Pull request

```bash
gh pr list --head "$HEAD_BRANCH" --base "$BASE" --state open --json url,number
```

- Si ya hay uno abierto → usá esa URL (no dupliques).
- Si no, crealo. Título: `feat: <qué se hizo>` en una línea (de la orden). Cuerpo en un archivo, así no hay
  problemas de comillas en Windows:

```markdown
## Summary
- <qué hace la entrega, 3–6 viñetas desde ENTREGA.md y la especificación>

## Cambios principales
- <módulos / endpoints / componentes>

## Test plan
- [ ] <cómo se verifica: comando de verificación y resultado, tests, colecciones, etc.>

## Entrega
<resumen de ENTREGA.md: tareas integradas, verificación, avisos (tests inestables, pendientes de verificar)>
```

```bash
gh pr create --base "$BASE" --head "$HEAD_BRANCH" --title "<título>" --body-file <archivo-temporal>
```

Con `pr.draft: true` en la regla → agregá `--draft`. El archivo del cuerpo va en una carpeta temporal del sistema,
**no** en el proyecto.

## 5. Verificar

```bash
gh pr view "$HEAD_BRANCH" --json url,number,state,baseRefName,headRefName
```

`baseRefName` tiene que ser `BASE`. Si quedó contra otra rama: `gh pr edit --base "$BASE"`.

## Resultado

Formato de la regla `pr-output-format`, con la línea **`ENTREGABLE: <pr_url>`**.

## Errores comunes

| Situación | Qué hacer |
|-----------|-----------|
| Solo push, sin pull request | Seguir hasta `gh pr create` |
| `No commits between …` | Bloqueado: la rama no tiene cambios contra la base |
| `origin/develop` no existe | Usar la rama de partida de la corrida (paso 2.3) |
| `Win32 error 5`, `Schannel` sin credenciales, `Permission denied` al hacer push | El entorno de la herramienta no deja a git usar tus credenciales (ej. el sandbox de Codex en Windows): `RESULTADO: bloqueado` con esa causa; este rol tiene que correr en una herramienta sin sandbox (Claude Code) |
| `gh` sin sesión | Bloqueado: el usuario corre `gh auth login` |

## Checklist

- [ ] `gh auth status` OK y remoto `origin` en GitHub
- [ ] Base resuelta según `pr-base-branch` y `origin/<base>` existe
- [ ] Rama de la corrida publicada (`git push -u origin HEAD`), sin force push
- [ ] Pull request creado o reutilizado, con `--base` explícito y Summary + Test plan
- [ ] `baseRefName` verificado con `gh pr view`
- [ ] Respuesta con `ENTREGABLE: <url del pull request>`
