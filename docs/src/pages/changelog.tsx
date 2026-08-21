import type {ReactNode} from 'react';
import Layout from '@theme/Layout';

export default function Changelog(): ReactNode {
  return (
    <Layout title="Changelog" description="Windrunner Version History and Release Notes">
      <main style={{maxWidth: '900px', margin: '0 auto', padding: '3rem 1.5rem'}}>
        <h1>Changelog</h1>
        <p style={{fontSize: '1.2rem', color: 'var(--ifm-color-emphasis-700)'}}>
          Release history and product updates for Windrunner.
        </p>

        <article style={{marginTop: '3rem', paddingBottom: '2rem', borderBottom: '1px solid var(--ifm-color-emphasis-300)'}}>
          <div style={{display: 'flex', alignItems: 'baseline', gap: '1rem'}}>
            <h2 style={{margin: 0}}>v0.2.0</h2>
            <span style={{fontSize: '0.9rem', color: 'var(--ifm-color-emphasis-600)'}}>August 2026</span>
          </div>
          <ul style={{marginTop: '1rem'}}>
            <li>
              <strong>Gemini Interactions API Migration</strong>: Migrated Google LLM service from legacy <code>generateContent</code> to the stateful <code>/v1beta/interactions</code> API.
            </li>
            <li>
              <strong>Flyway Database Migrations</strong>: Integrated Flyway versioned schema migrations (<code>V1__initial_schema.sql</code>) for automatic startup migration and baseline management.
            </li>
            <li>
              <strong>Spring Boot Core</strong>: Updated database configuration and security models for structured work editor workflows.
            </li>
          </ul>
        </article>

        <article style={{marginTop: '2.5rem'}}>
          <div style={{display: 'flex', alignItems: 'baseline', gap: '1rem'}}>
            <h2 style={{margin: 0}}>v0.1.0</h2>
            <span style={{fontSize: '0.9rem', color: 'var(--ifm-color-emphasis-600)'}}>Initial Release</span>
          </div>
          <ul style={{marginTop: '1rem'}}>
            <li>Initial release of Windrunner structured work editor.</li>
            <li>Three-layer role access control (Global, Team, Project).</li>
            <li>Support for work item semantic graphs and AI change proposals.</li>
          </ul>
        </article>
      </main>
    </Layout>
  );
}
