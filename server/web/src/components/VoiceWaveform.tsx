type VoiceWaveformProps = {
  levels: number[]
}

export default function VoiceWaveform({ levels }: VoiceWaveformProps) {
  return (
    <div className="flex h-7 w-28 items-center justify-center gap-0.5 overflow-hidden" aria-hidden="true">
      {levels.map((level, index) => (
        <span
          key={index}
          className="w-0.5 rounded-full bg-blue-500 transition-[height] duration-75"
          style={{ height: `${Math.max(3, Math.round(4 + Math.min(1, level) * 22))}px` }}
        />
      ))}
    </div>
  )
}
