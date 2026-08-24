# Windrunner documentation site

This directory contains the Docusaurus documentation site for Windrunner.

## Local development

```bash
cd docs
npm ci
npm run start
```

The development server opens the site locally with hot reload.

## Checks

Build the production site and run the TypeScript check before submitting docs
changes:

```bash
npm run build
npm run typecheck
```

The generated site is written to `docs/build` and can be previewed with:

```bash
npm run serve
```

## Deployment

Pushes to `main` build and deploy the site through GitHub Pages using
[`deploy-docs.yml`](../.github/workflows/deploy-docs.yml). The repository's
Pages source must be set to **GitHub Actions**.
