type ChartRendererProps = {
  artifact: {
    title: string | null
    content: string
  }
}

function formatChartContent(content: string) {
  try {
    return JSON.stringify(JSON.parse(content), null, 2)
  } catch {
    return content
  }
}

export default function ChartRenderer({ artifact }: ChartRendererProps) {
  return (
    <div className="space-y-3">
      <div className="text-sm font-medium">{artifact.title?.trim() || 'Chart artifact'}</div>
      <pre className="overflow-auto whitespace-pre-wrap break-all border border-black bg-muted/20 p-3 font-mono text-xs leading-relaxed">
        {formatChartContent(artifact.content)}
      </pre>
    </div>
  )
}
