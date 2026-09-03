import { useCallback, useEffect, useRef, useState } from 'react'
import type { RefObject } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { getAudioTranscriptionStatus, transcribeAudio } from '@/lib/api'

function supportedRecordingMimeType() {
  if (typeof MediaRecorder === 'undefined') {
    return ''
  }

  return [
    'audio/webm;codecs=opus',
    'audio/mp4',
    'audio/ogg;codecs=opus',
  ].find((mimeType) => MediaRecorder.isTypeSupported(mimeType)) ?? ''
}

function recordingFileExtension(mimeType: string) {
  const normalizedMimeType = mimeType.toLowerCase()
  if (normalizedMimeType.includes('mp4')) return 'mp4'
  if (normalizedMimeType.includes('ogg')) return 'ogg'
  return 'webm'
}

const defaultWaveformLevels = Array.from({ length: 32 }, () => 0.08)

export function formatRecordingTime(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`
}

type UseVoiceTranscriptionOptions = {
  value: string
  onValueChange: (value: string) => void
  inputRef: RefObject<HTMLTextAreaElement | null>
  disabled?: boolean
}

type MicrophonePermission = PermissionState | 'unknown'

export default function useVoiceTranscription({
  value,
  onValueChange,
  inputRef,
  disabled = false,
}: UseVoiceTranscriptionOptions) {
  const { t } = useTranslation()
  const [available, setAvailable] = useState(false)
  const [maxRecordingSeconds, setMaxRecordingSeconds] = useState(120)
  const [isRecording, setIsRecording] = useState(false)
  const [isTranscribing, setIsTranscribing] = useState(false)
  const [recordingSeconds, setRecordingSeconds] = useState(0)
  const [transcriptionError, setTranscriptionError] = useState<string | null>(null)
  const [microphonePermission, setMicrophonePermission] = useState<MicrophonePermission>('unknown')
  const [waveformLevels, setWaveformLevels] = useState(defaultWaveformLevels)
  const transcriptionAbortControllerRef = useRef<AbortController | null>(null)
  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const mediaStreamRef = useRef<MediaStream | null>(null)
  const recordingChunksRef = useRef<Blob[]>([])
  const recordingTimerRef = useRef<number | null>(null)
  const recordingMimeTypeRef = useRef('')
  const recordingCancelledRef = useRef(false)
  const recordingSelectionRef = useRef<number | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const audioSourceRef = useRef<MediaStreamAudioSourceNode | null>(null)
  const analyserRef = useRef<AnalyserNode | null>(null)
  const waveformDataRef = useRef<Uint8Array<ArrayBuffer> | null>(null)
  const waveformAnimationRef = useRef<number | null>(null)

  const clearRecordingTimer = useCallback(() => {
    if (recordingTimerRef.current !== null) {
      window.clearInterval(recordingTimerRef.current)
      recordingTimerRef.current = null
    }
  }, [])

  const cleanupRecordingResources = useCallback(() => {
    clearRecordingTimer()

    const recorder = mediaRecorderRef.current
    if (recorder && recorder.state !== 'inactive') {
      recorder.ondataavailable = null
      recorder.onstop = null
      recorder.onerror = null
      try {
        recorder.stop()
      } catch {
        // The recorder may already be stopping as the component unmounts.
      }
    }

    mediaStreamRef.current?.getTracks().forEach((track) => track.stop())
    mediaRecorderRef.current = null
    mediaStreamRef.current = null
    recordingChunksRef.current = []
    recordingMimeTypeRef.current = ''
    if (waveformAnimationRef.current !== null) {
      window.cancelAnimationFrame(waveformAnimationRef.current)
      waveformAnimationRef.current = null
    }
    audioSourceRef.current?.disconnect()
    audioSourceRef.current = null
    analyserRef.current = null
    waveformDataRef.current = null
    const audioContext = audioContextRef.current
    audioContextRef.current = null
    if (audioContext) {
      void audioContext.close().catch(() => undefined)
    }
    setWaveformLevels(defaultWaveformLevels)
  }, [clearRecordingTimer])

  const voiceInputSupported = typeof MediaRecorder !== 'undefined'
    && typeof navigator !== 'undefined'
    && Boolean(navigator.mediaDevices?.getUserMedia)

  useEffect(() => {
    let isMounted = true

    async function loadTranscriptionStatus() {
      try {
        const status = await getAudioTranscriptionStatus()
        if (!isMounted) return
        setAvailable(status.available)
        setMaxRecordingSeconds(Math.max(1, status.maxDurationSeconds || 120))
      } catch {
        if (isMounted) {
          setAvailable(false)
        }
      }
    }

    void loadTranscriptionStatus()

    return () => {
      isMounted = false
    }
  }, [])

  useEffect(() => {
    if (typeof navigator === 'undefined' || !navigator.permissions?.query) {
      return
    }

    let isMounted = true
    let permissionStatus: PermissionStatus | null = null

    navigator.permissions.query({ name: 'microphone' }).then((status) => {
      if (!isMounted) return
      permissionStatus = status
      setMicrophonePermission(status.state)
      status.onchange = () => setMicrophonePermission(status.state)
    }).catch(() => {
      // Some browsers do not expose microphone permission state.
    })

    return () => {
      isMounted = false
      if (permissionStatus) {
        permissionStatus.onchange = null
      }
    }
  }, [])

  useEffect(() => {
    return () => {
      transcriptionAbortControllerRef.current?.abort()
      cleanupRecordingResources()
    }
  }, [cleanupRecordingResources])

  useEffect(() => {
    if (!isRecording || recordingSeconds < maxRecordingSeconds) {
      return
    }

    const recorder = mediaRecorderRef.current
    if (recorder?.state === 'recording') {
      recorder.stop()
    }
  }, [isRecording, maxRecordingSeconds, recordingSeconds])

  function stopRecording() {
    const recorder = mediaRecorderRef.current
    if (!recorder || recorder.state === 'inactive') {
      return
    }
    recorder.stop()
  }

  function cancelRecording() {
    recordingCancelledRef.current = true
    stopRecording()
  }

  function insertTranscription(transcription: string) {
    const selectionStart = Math.min(
      Math.max(recordingSelectionRef.current ?? value.length, 0),
      value.length,
    )
    const before = value.slice(0, selectionStart)
    const after = value.slice(selectionStart)
    const beforeSeparator = before && !/\s$/.test(before) ? ' ' : ''
    const afterSeparator = after && !/^\s/.test(after) ? ' ' : ''
    const nextValue = `${before}${beforeSeparator}${transcription}${afterSeparator}${after}`
    const nextCaretPosition = before.length + beforeSeparator.length + transcription.length

    onValueChange(nextValue)
    recordingSelectionRef.current = null
    window.setTimeout(() => {
      const input = inputRef.current
      if (!input) return
      input.focus()
      input.setSelectionRange(nextCaretPosition, nextCaretPosition)
    }, 0)
  }

  async function transcribeRecordedAudio(audio: Blob, mimeType: string) {
    setIsTranscribing(true)
    setTranscriptionError(null)
    const controller = new AbortController()
    transcriptionAbortControllerRef.current = controller
    const fileName = `voice-message.${recordingFileExtension(mimeType)}`

    try {
      const response = await transcribeAudio(audio, fileName, undefined, controller.signal)
      const transcription = response.text?.trim()
      if (!transcription) {
        throw new Error(t('chat.noSpeechDetected'))
      }
      insertTranscription(transcription)
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }
      const errorMessage = error instanceof Error ? error.message : t('chat.voiceTranscriptionFailed')
      setTranscriptionError(errorMessage)
      toast.error(errorMessage)
    } finally {
      if (transcriptionAbortControllerRef.current === controller) {
        transcriptionAbortControllerRef.current = null
        setIsTranscribing(false)
      }
    }
  }

  async function startRecording() {
    if (disabled || isRecording || isTranscribing) {
      return
    }
    if (!available) {
      toast.error(t('chat.voiceTranscriptionUnavailable'))
      return
    }
    if (!voiceInputSupported) {
      toast.error(t('chat.microphoneNotSupported'))
      return
    }

    if (microphonePermission !== 'granted') {
      toast.info(t('chat.microphonePermissionPrompt'))
    }

    let stream: MediaStream | null = null
    try {
      recordingSelectionRef.current = inputRef.current?.selectionStart ?? value.length
      stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      })
      setMicrophonePermission('granted')

      const preferredMimeType = supportedRecordingMimeType()
      const recorder = preferredMimeType
        ? new MediaRecorder(stream, { mimeType: preferredMimeType })
        : new MediaRecorder(stream)

      try {
        const audioContextConstructor = window.AudioContext
          ?? (window as Window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
        if (audioContextConstructor) {
          const audioContext = new audioContextConstructor()
          const analyser = audioContext.createAnalyser()
          analyser.fftSize = 64
          analyser.smoothingTimeConstant = 0.8
          const source = audioContext.createMediaStreamSource(stream)
          source.connect(analyser)
          audioContextRef.current = audioContext
          audioSourceRef.current = source
          analyserRef.current = analyser
          waveformDataRef.current = new Uint8Array(analyser.fftSize)

          const updateWaveform = () => {
            const currentAnalyser = analyserRef.current
            const waveformData = waveformDataRef.current
            if (!currentAnalyser || !waveformData) return

            currentAnalyser.getByteTimeDomainData(waveformData)
            const nextLevels = Array.from({ length: 32 }, (_value, index) => {
              const start = Math.floor(index * waveformData.length / 32)
              const end = Math.max(start + 1, Math.floor((index + 1) * waveformData.length / 32))
              let peak = 0
              for (let sampleIndex = start; sampleIndex < end; sampleIndex += 1) {
                peak = Math.max(peak, Math.abs(waveformData[sampleIndex] - 128) / 128)
              }
              return peak
            })
            setWaveformLevels(nextLevels)
            waveformAnimationRef.current = window.requestAnimationFrame(updateWaveform)
          }

          void audioContext.resume().catch(() => undefined)
          updateWaveform()
        }
      } catch {
        // Recording still works when Web Audio analysis is unavailable.
      }

      mediaStreamRef.current = stream
      mediaRecorderRef.current = recorder
      recordingChunksRef.current = []
      recordingMimeTypeRef.current = recorder.mimeType || preferredMimeType || 'audio/webm'
      recordingCancelledRef.current = false
      setRecordingSeconds(0)
      setTranscriptionError(null)

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          recordingChunksRef.current.push(event.data)
        }
      }
      recorder.onerror = () => {
        recordingCancelledRef.current = true
        cleanupRecordingResources()
        setIsRecording(false)
        setRecordingSeconds(0)
        toast.error(t('chat.voiceRecordingFailed'))
      }
      recorder.onstop = () => {
        const wasCancelled = recordingCancelledRef.current
        const mimeType = recorder.mimeType || recordingMimeTypeRef.current || 'audio/webm'
        const chunks = [...recordingChunksRef.current]
        cleanupRecordingResources()
        setIsRecording(false)
        setRecordingSeconds(0)
        recordingCancelledRef.current = false

        if (wasCancelled) {
          return
        }

        const audio = new Blob(chunks, { type: mimeType })
        if (!audio.size) {
          setTranscriptionError(t('chat.noSpeechDetected'))
          toast.error(t('chat.noSpeechDetected'))
          return
        }
        void transcribeRecordedAudio(audio, mimeType)
      }

      recorder.start()
      setIsRecording(true)
      recordingTimerRef.current = window.setInterval(() => {
        setRecordingSeconds((seconds) => seconds + 1)
      }, 1000)
    } catch (error) {
      cleanupRecordingResources()
      stream?.getTracks().forEach((track) => track.stop())
      recordingSelectionRef.current = null
      if (error instanceof DOMException && (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError')) {
        setMicrophonePermission('denied')
        const permissionError = t('chat.microphonePermissionDenied')
        setTranscriptionError(permissionError)
        toast.error(permissionError)
      } else {
        const recordingError = t('chat.voiceRecordingFailed')
        setTranscriptionError(recordingError)
        toast.error(recordingError)
      }
    }
  }

  return {
    available,
    voiceInputSupported,
    isRecording,
    isTranscribing,
    isBusy: isRecording || isTranscribing,
    recordingSeconds,
    transcriptionError,
    microphonePermission,
    waveformLevels,
    startRecording,
    stopRecording,
    cancelRecording,
  }
}
