import type {ReactNode} from 'react';

type DataModelNodeTone = 'identity' | 'graph' | 'agent';

type DataModelNodeProps = {
  x: number;
  y: number;
  width: number;
  title: string;
  subtitle: string;
  tone: DataModelNodeTone;
};

function DataModelNode({x, y, width, title, subtitle, tone}: DataModelNodeProps): ReactNode {
  return (
    <g
      className={`home-model-node home-model-node--${tone}`}
      transform={`translate(${x} ${y})`}
    >
      <rect width={width} height="68" rx="14" />
      <text className="home-model-node-title" x="16" y="28">
        {title}
      </text>
      <text className="home-model-node-subtitle" x="16" y="49">
        {subtitle}
      </text>
    </g>
  );
}

export default function WorkGraphModel(): ReactNode {
  return (
    <section className="home-model-section" aria-labelledby="home-model-title">
      <div className="container">
        <div className="home-model-heading">
          <p className="home-model-eyebrow">How it works</p>
          <h2 id="home-model-title">Everything connects through the Work Graph.</h2>
          <p>
            People and teams get access to projects, projects organize work,
            and every update, dependency, and decision stays connected for
            humans and the AI Agent.
          </p>
        </div>

        <figure className="home-model-figure">
          <div className="home-model-legend" aria-hidden="true">
            <span><i className="home-model-legend-line home-model-legend-line--solid" />Record relationship</span>
            <span><i className="home-model-legend-line home-model-legend-line--dashed" />Agent, context, or activity flow</span>
          </div>
          <div className="home-model-scroll">
            <svg
              className="home-model-svg"
              viewBox="0 0 1200 700"
              role="img"
              aria-labelledby="home-model-svg-title home-model-svg-description"
              focusable="false"
            >
              <title id="home-model-svg-title">Windrunner connected data model</title>
              <desc id="home-model-svg-description">
                Users and teams connect to projects through memberships. Projects contain work items,
                entries, relationships, and assignees. Chat sessions provide context to the AI Agent,
                which creates reviewable proposals. Calendar events and activity stay connected to the work.
              </desc>
              <defs>
                <marker
                  id="home-model-arrow"
                  markerWidth="7"
                  markerHeight="7"
                  refX="6"
                  refY="3.5"
                  orient="auto"
                  markerUnits="userSpaceOnUse"
                >
                  <path d="M0 0 L7 3.5 L0 7 Z" fill="currentColor" />
                </marker>
              </defs>

              <g className="home-model-groups" aria-hidden="true">
                <rect className="home-model-group home-model-group--identity" x="24" y="52" width="282" height="548" rx="24" />
                <rect className="home-model-group home-model-group--graph" x="330" y="52" width="540" height="548" rx="24" />
                <rect className="home-model-group home-model-group--agent" x="894" y="52" width="282" height="600" rx="24" />
                <text className="home-model-group-label" x="50" y="88">PEOPLE &amp; ACCESS</text>
                <text className="home-model-group-label" x="356" y="88">WORK GRAPH</text>
                <text className="home-model-group-label" x="920" y="88">AI &amp; ACTIVITY</text>
              </g>

              <g className="home-model-connectors" aria-hidden="true">
                <path className="home-model-connector home-model-connector--identity" d="M165 198 C165 220 165 240 165 262" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--identity" d="M280 164 C322 164 322 464 280 464" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--identity" d="M280 294 C322 294 322 464 280 464" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--identity" d="M280 464 C312 464 328 164 360 164" markerEnd="url(#home-model-arrow)" />

                <path className="home-model-connector home-model-connector--graph" d="M570 164 C585 164 595 164 610 164" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--graph" d="M720 198 C700 240 600 276 570 336" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--graph" d="M720 198 C720 230 720 268 720 302" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--graph" d="M570 336 C585 336 595 336 610 336" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--graph" d="M720 198 C700 270 650 390 595 456" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--graph" d="M280 464 C360 505 410 490 485 490" markerEnd="url(#home-model-arrow)" />

                <path className="home-model-connector home-model-connector--agent" d="M280 145 C450 10 790 10 920 145" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--agent" d="M920 155 C800 78 690 78 570 150" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--agent" d="M1030 198 C1030 230 1030 252 1030 284" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--agent" d="M920 318 C875 318 855 336 830 336" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--agent" d="M920 472 C875 472 875 228 830 180" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--agent" d="M830 180 C865 230 875 520 920 574" markerEnd="url(#home-model-arrow)" />
                <path className="home-model-connector home-model-connector--agent" d="M1140 352 C1160 410 1160 510 1140 540" markerEnd="url(#home-model-arrow)" />
              </g>

              <g className="home-model-edge-labels" aria-hidden="true">
                <text className="home-model-edge-label" x="178" y="235">member of</text>
                <text className="home-model-edge-label" x="304" y="316">access</text>
                <text className="home-model-edge-label" x="588" y="151">contains</text>
                <text className="home-model-edge-label" x="615" y="253">updates</text>
                <text className="home-model-edge-label" x="730" y="253">links</text>
                <text className="home-model-edge-label" x="620" y="442">assigned to</text>
                <text className="home-model-edge-label" x="365" y="485">eligible</text>
                <text className="home-model-edge-label" x="567" y="45">starts</text>
                <text className="home-model-edge-label" x="718" y="78">context</text>
                <text className="home-model-edge-label" x="1044" y="244">proposes</text>
                <text className="home-model-edge-label" x="852" y="304">changes</text>
                <text className="home-model-edge-label" x="858" y="386">scheduled</text>
                <text className="home-model-edge-label" x="858" y="532">activity</text>
                <text className="home-model-edge-label" x="1160" y="450">reviewed</text>
              </g>

              <DataModelNode x={50} y={130} width={230} title="User" subtitle="identity + profile" tone="identity" />
              <DataModelNode x={50} y={262} width={230} title="Team" subtitle="shared ownership" tone="identity" />
              <DataModelNode x={50} y={430} width={230} title="Memberships" subtitle="team + project access" tone="identity" />

              <DataModelNode x={360} y={130} width={210} title="Project" subtitle="workspace boundary" tone="graph" />
              <DataModelNode x={610} y={130} width={220} title="Work item" subtitle="hierarchy + progress" tone="graph" />
              <DataModelNode x={360} y={302} width={210} title="Entry" subtitle="updates + evidence" tone="graph" />
              <DataModelNode x={610} y={302} width={220} title="Relationship" subtitle="typed links" tone="graph" />
              <DataModelNode x={485} y={456} width={220} title="Assignees" subtitle="user or team" tone="graph" />

              <DataModelNode x={920} y={130} width={220} title="Chat session" subtitle="messages + context" tone="agent" />
              <DataModelNode x={920} y={284} width={220} title="AI proposal" subtitle="reviewable changes" tone="agent" />
              <DataModelNode x={920} y={438} width={220} title="Calendar event" subtitle="time + work item" tone="agent" />
              <DataModelNode x={920} y={540} width={220} title="Activity" subtitle="audit + notifications" tone="agent" />
            </svg>
          </div>
          <figcaption>
            The Work Graph keeps access, hierarchy, context, ownership, and time connected while every AI change remains reviewable.
          </figcaption>
        </figure>
      </div>
    </section>
  );
}
