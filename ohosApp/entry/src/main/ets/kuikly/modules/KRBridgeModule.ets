import { abilityAccessCtrl, common, Permissions } from '@kit.AbilityKit';
import { audio } from '@kit.AudioKit';
import { media } from '@kit.MediaKit';
import { image } from '@kit.ImageKit';
import { promptAction } from '@kit.ArkUI';
import { BusinessError, pasteboard } from '@kit.BasicServicesKit';
import { KRAny } from '@kuikly-open/render';
import { KuiklyRenderBaseModule } from '@kuikly-open/render';
import { KuiklyRenderCallback } from '@kuikly-open/render';
import util from '@ohos.util';
import fs from '@ohos.file.fs';
import { fileUri } from '@kit.CoreFileKit';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { StockChatStreamRequest } from './StockChatStreamRequest';

// 与 Android 端一致：16kHz 单声道 16bit PCM WAV，最短 300ms，最长 30s
const VOICE_SAMPLE_RATE_HZ: number = 16000;
const VOICE_CHANNEL_COUNT: number = 1;
const VOICE_BITS_PER_SAMPLE: number = 16;
const VOICE_BYTES_PER_SAMPLE: number = VOICE_BITS_PER_SAMPLE / 8;
const VOICE_MIN_DURATION_MS: number = 300;
const VOICE_MAX_DURATION_SECONDS: number = 30;
const VOICE_MAX_PCM_BYTES: number =
  VOICE_SAMPLE_RATE_HZ * VOICE_CHANNEL_COUNT * VOICE_BYTES_PER_SAMPLE * VOICE_MAX_DURATION_SECONDS;
const VOICE_WAV_HEADER_SIZE: number = 44;
const VOICE_WAV_MIME_TYPE: string = 'audio/wav';
const MICROPHONE_PERMISSION: Permissions = 'ohos.permission.MICROPHONE';
const MAX_PLAYBACK_AUDIO_BYTES: number = 24 * 1024 * 1024;
// 与 iOS / Android 一致：最长边 2048，JPEG 质量 82
const MAX_IMAGE_DIMENSION: number = 2048;
const IMAGE_JPEG_QUALITY: number = 82;

class VoiceRecordingSession {
  capturer: audio.AudioCapturer;
  chunks: Uint8Array[] = [];
  totalBytes: number = 0;
  capturing: boolean = true;
  captureError: string = '';

  constructor(capturer: audio.AudioCapturer) {
    this.capturer = capturer;
  }
}

export class KRBridgeModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = "HRBridgeModule";
  static readonly SHOW_ALERT = "showAlert";
  static readonly CLOSE_PAGE = "closePage";
  static readonly OPEN_PAGE = "openPage";
  static readonly COPY_TO_PASTE_BOARD = "copyToPasteboard";
  static readonly TOAST = "toast";
  static readonly LOG = "log";
  static readonly REPORT_DT = "reportDT";
  static readonly REPORT_REAL_TIME = "reportRealtime";
  static readonly LOCAL_SERVE_TIME = "localServeTime";
  static readonly CURRENT_TIME_STAMP = "currentTimestamp";
  static readonly DATE_FORMATTER = "dateFormatter";
  static readonly READ_ASSET_FILE = "readAssetFile";
  static readonly START_VOICE_RECORDING = "startVoiceRecording";
  static readonly STOP_VOICE_RECORDING = "stopVoiceRecording";
  static readonly CANCEL_VOICE_RECORDING = "cancelVoiceRecording";
  static readonly PLAY_BASE64_AUDIO = "playBase64Audio";
  static readonly STOP_AUDIO_PLAYBACK = "stopAudioPlayback";
  static readonly STREAM_CHAT_COMPLETION = "streamChatCompletion";
  static readonly PICK_IMAGES = "pickImages";
  private imagePickerPending: boolean = false;
  // 语音录音状态机：与 Android 端 KRBridgeModule 对齐
  private voiceRecording: VoiceRecordingSession | null = null;
  private voiceStartPending: boolean = false;
  private voiceCleanupInProgress: boolean = false;
  private voiceStartSequence: number = 0;
  private pendingVoiceStartCallback: KuiklyRenderCallback | null = null;
  // 朗读播放：一次只保留一个播放器，新请求会顶掉旧的
  private audioPlayer: media.AVPlayer | null = null;
  private audioPlaybackFd: number = -1;
  private audioPlaybackFile: string = '';
  private audioPlaybackSequence: number = 0;
  private chatStreams: Set<StockChatStreamRequest> = new Set();

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback): KRAny {
    switch (method) {
      case KRBridgeModule.SHOW_ALERT: {
        this.showAlert(params, callback);
        break;
      }
      case KRBridgeModule.CLOSE_PAGE: {
        this.closePage();
        break;
      }
      case KRBridgeModule.OPEN_PAGE: {
        this.openPage();
        break;
      }
      case KRBridgeModule.COPY_TO_PASTE_BOARD: {
        this.copyToPasteboard(params);
        break;
      }
      case KRBridgeModule.TOAST: {
        this.toast(params);
        break;
      }
      case KRBridgeModule.LOG: {
        this.log(params);
        break;
      }
      case KRBridgeModule.REPORT_DT: {
        this.reportDT(params);
        break;
      }
      case KRBridgeModule.REPORT_REAL_TIME: {
        this.reportRealtime(params);
        break;
      }
      case KRBridgeModule.LOCAL_SERVE_TIME: {
        this.localServeTime(params, callback);
        break;
      }
      case KRBridgeModule.CURRENT_TIME_STAMP: {
        return this.currentTimestamp();
      }
      case KRBridgeModule.DATE_FORMATTER: {
        return this.dateFormatter(params);
      }
      case KRBridgeModule.READ_ASSET_FILE: {
        this.readAssetFile(params, callback);
        break;
      }
      case KRBridgeModule.START_VOICE_RECORDING: {
        this.startVoiceRecording(callback);
        break;
      }
      case KRBridgeModule.STOP_VOICE_RECORDING: {
        this.stopVoiceRecording(callback);
        break;
      }
      case KRBridgeModule.CANCEL_VOICE_RECORDING: {
        this.cancelVoiceRecording(callback);
        break;
      }
      case KRBridgeModule.PLAY_BASE64_AUDIO: {
        this.playBase64Audio(params, callback);
        break;
      }
      case KRBridgeModule.STOP_AUDIO_PLAYBACK: {
        this.stopAudioPlayback(callback);
        break;
      }
      case KRBridgeModule.STREAM_CHAT_COMPLETION: {
        this.streamChatCompletion(params, callback);
        break;
      }
      case KRBridgeModule.PICK_IMAGES: {
        this.pickImages(params, callback);
        break;
      }
      default:
        break;
    }
    return null;
  }

  private showAlert(params: KRAny, callback: KuiklyRenderCallback) {
  }

  // TODO close page
  private closePage() {
  }

  // TODO open new page
  private openPage() {
  }

  private copyToPasteboard(params: KRAny) {
    if (params == null) {
      return;
    }
    let records = JSON.parse(params as string) as Record<string, string>;
    let content = records["content"];
    if (content == null || content.length === 0) {
      return;
    }
    let pasteData = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, content);
    pasteboard.getSystemPasteboard().setData(pasteData).catch((error: BusinessError) => {
      console.error(`KuiklyRender copyToPasteboard failed: ${error.code} ${error.message}`);
    });
  }

  private toast(params: KRAny) {
    if (params == null || typeof params !== 'string') {
      return;
    }
    try {
      const records = JSON.parse(params as string) as Record<string, string>;
      const content = records['content'];
      if (typeof content === 'string' && content.length > 0) {
        promptAction.showToast({ message: content });
      }
    } catch (error) {
      console.error(`KuiklyRender toast failed: ${JSON.stringify(error)}`);
    }
  }

  private log(params: KRAny) {
    if (params == null) {
      return
    }
    console.log(`KuiklyRender ${JSON.stringify(params)}`,)
  }

  private reportDT(params: KRAny) {
  }

  private reportRealtime(params: KRAny) {
  }

  private localServeTime(params: KRAny, callback: KuiklyRenderCallback) {
    callback({ "time": Date.now() / 1000.0 });
  }

  private currentTimestamp(): string {
    return Date.now().toString()
  }

  // TODO format date
  private dateFormatter(params: KRAny): string {
    console.log(`========== ${params}`)
    return "";
  }

  private startVoiceRecording(callback: KuiklyRenderCallback | null) {
    if (this.voiceRecording != null) {
      callback?.(this.voiceFailure('ALREADY_RECORDING', '语音录音正在进行中。'));
      return;
    }
    if (this.voiceStartPending) {
      callback?.(this.voiceFailure('START_IN_PROGRESS', '正在申请麦克风权限，请稍候。'));
      return;
    }
    if (this.voiceCleanupInProgress) {
      callback?.(this.voiceFailure('RECORDING_CLEANUP_IN_PROGRESS', '上一段录音正在处理，请稍候。'));
      return;
    }
    this.voiceStartPending = true;
    this.pendingVoiceStartCallback = callback;
    this.voiceStartSequence += 1;
    const sequence = this.voiceStartSequence;
    this.requestMicrophonePermission().then((granted: boolean) => {
      // 权限弹窗期间被 cancelVoiceRecording 打断：回调已由取消路径送出
      if (!this.voiceStartPending || sequence !== this.voiceStartSequence) {
        return;
      }
      if (!granted) {
        this.voiceStartPending = false;
        const startCallback = this.pendingVoiceStartCallback;
        this.pendingVoiceStartCallback = null;
        startCallback?.(this.voiceFailure('RECORD_AUDIO_PERMISSION_DENIED', '需要麦克风权限才能使用语音输入。'));
        return;
      }
      this.startAudioCapture(sequence);
    });
  }

  private async requestMicrophonePermission(): Promise<boolean> {
    const context = this.hostAbilityContext();
    if (context == null) {
      return false;
    }
    try {
      const atManager = abilityAccessCtrl.createAtManager();
      const result = await atManager.requestPermissionsFromUser(context, [MICROPHONE_PERMISSION]);
      const authResults = result.authResults;
      return authResults.length > 0 && authResults[0] === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
    } catch (error) {
      console.error(`KuiklyRender request microphone permission failed: ${JSON.stringify(error)}`);
      return false;
    }
  }

  private hostAbilityContext(): common.UIAbilityContext | null {
    const uiContext = AppStorage.get<UIContext>('context');
    const hostContext = uiContext?.getHostContext();
    if (hostContext != null) {
      return hostContext as common.UIAbilityContext;
    }
    const fallback = getContext(this);
    return fallback != null ? fallback as common.UIAbilityContext : null;
  }

  private startAudioCapture(sequence: number) {
    if (!canIUse('SystemCapability.Multimedia.Audio.Capturer')) {
      this.failPendingVoiceStart(sequence, 'AUDIO_RECORD_INIT_FAILED', '当前设备不支持录音。');
      return;
    }
    const options: audio.AudioCapturerOptions = {
      streamInfo: {
        samplingRate: audio.AudioSamplingRate.SAMPLE_RATE_16000,
        channels: audio.AudioChannel.CHANNEL_1,
        sampleFormat: audio.AudioSampleFormat.SAMPLE_FORMAT_S16LE,
        encodingType: audio.AudioEncodingType.ENCODING_TYPE_RAW,
      },
      capturerInfo: {
        source: audio.SourceType.SOURCE_TYPE_VOICE_RECOGNITION,
        capturerFlags: 0,
      },
    };
    audio.createAudioCapturer(options).then((capturer: audio.AudioCapturer) => {
      if (sequence !== this.voiceStartSequence) {
        this.releaseCapturer(capturer);
        return;
      }
      const session = new VoiceRecordingSession(capturer);
      capturer.on('readData', (buffer: ArrayBuffer) => {
        this.appendVoiceChunk(session, buffer);
      });
      capturer.start().then(() => {
        if (sequence !== this.voiceStartSequence) {
          session.capturing = false;
          this.releaseCapturer(capturer);
          return;
        }
        this.voiceStartPending = false;
        const startCallback = this.pendingVoiceStartCallback;
        this.pendingVoiceStartCallback = null;
        this.voiceRecording = session;
        startCallback?.({ 'success': 1 });
      }).catch((error: BusinessError) => {
        session.capturing = false;
        this.releaseCapturer(capturer);
        this.failPendingVoiceStart(sequence, 'RECORDING_START_FAILED',
          error.message || '无法启动麦克风，请稍后重试。');
      });
    }).catch((error: BusinessError) => {
      this.failPendingVoiceStart(sequence, 'AUDIO_RECORD_INIT_FAILED',
        error.message || '设备不支持 16kHz 单声道录音。');
    });
  }

  private failPendingVoiceStart(sequence: number, errorCode: string, errorMessage: string) {
    if (!this.voiceStartPending || sequence !== this.voiceStartSequence) {
      return;
    }
    this.voiceStartPending = false;
    const startCallback = this.pendingVoiceStartCallback;
    this.pendingVoiceStartCallback = null;
    startCallback?.(this.voiceFailure(errorCode, errorMessage));
  }

  private appendVoiceChunk(session: VoiceRecordingSession, buffer: ArrayBuffer) {
    if (!session.capturing) {
      return;
    }
    const remaining = VOICE_MAX_PCM_BYTES - session.totalBytes;
    if (remaining <= 0) {
      return;
    }
    const length = Math.min(buffer.byteLength, remaining);
    if (length <= 0) {
      return;
    }
    // 系统会复用回调缓冲区，必须拷贝
    session.chunks.push(new Uint8Array(buffer.slice(0, length)));
    session.totalBytes += length;
  }

  private stopVoiceRecording(callback: KuiklyRenderCallback | null) {
    const recording = this.voiceRecording;
    if (recording == null) {
      callback?.(this.voiceFailure('NOT_RECORDING', '当前没有正在进行的语音录音。'));
      return;
    }
    this.voiceRecording = null;
    this.voiceCleanupInProgress = true;
    recording.capturing = false;
    this.releaseCapturer(recording.capturer).then(() => {
      let result: Record<string, Object>;
      try {
        result = this.finishVoiceRecording(recording);
      } catch (error) {
        console.error(`KuiklyRender finish voice recording failed: ${JSON.stringify(error)}`);
        result = this.voiceFailure('RECORDING_FINALIZE_FAILED', '处理语音录音失败。');
      }
      this.voiceCleanupInProgress = false;
      callback?.(result);
    });
  }

  private cancelVoiceRecording(callback: KuiklyRenderCallback | null) {
    // 让还在等待权限结果 / 采集器创建的启动请求失效
    this.voiceStartSequence += 1;
    this.voiceStartPending = false;
    const pendingCallback = this.pendingVoiceStartCallback;
    this.pendingVoiceStartCallback = null;
    pendingCallback?.(this.voiceFailure('RECORDING_CANCELLED', '语音录音已取消。'));

    const recording = this.voiceRecording;
    this.voiceRecording = null;
    if (recording == null) {
      callback?.({ 'success': 1 });
      return;
    }
    this.voiceCleanupInProgress = true;
    recording.capturing = false;
    this.releaseCapturer(recording.capturer).then(() => {
      recording.chunks = [];
      recording.totalBytes = 0;
      this.voiceCleanupInProgress = false;
      callback?.({ 'success': 1 });
    });
  }

  private async releaseCapturer(capturer: audio.AudioCapturer): Promise<void> {
    try {
      capturer.off('readData');
    } catch (error) {
      console.error(`KuiklyRender detach capturer failed: ${JSON.stringify(error)}`);
    }
    try {
      if (capturer.state === audio.AudioState.STATE_RUNNING) {
        await capturer.stop();
      }
    } catch (error) {
      console.error(`KuiklyRender stop capturer failed: ${JSON.stringify(error)}`);
    }
    try {
      await capturer.release();
    } catch (error) {
      console.error(`KuiklyRender release capturer failed: ${JSON.stringify(error)}`);
    }
  }

  private finishVoiceRecording(recording: VoiceRecordingSession): Record<string, Object> {
    if (recording.captureError.length > 0) {
      return this.voiceFailure('RECORDING_FAILED', recording.captureError);
    }
    if (recording.totalBytes === 0) {
      return this.voiceFailure('EMPTY_AUDIO', '没有录到有效的语音内容。');
    }
    const durationMs = Math.floor(recording.totalBytes * 1000 /
      (VOICE_SAMPLE_RATE_HZ * VOICE_BYTES_PER_SAMPLE * VOICE_CHANNEL_COUNT));
    if (durationMs < VOICE_MIN_DURATION_MS) {
      return this.voiceFailure('AUDIO_TOO_SHORT', `语音时间过短，请至少录制 ${VOICE_MIN_DURATION_MS}ms。`);
    }
    const wavBytes = this.buildWavFile(recording.chunks, recording.totalBytes);
    const base64 = new util.Base64Helper().encodeToStringSync(wavBytes);
    const result: Record<string, Object> = {
      'success': 1,
      'audioBase64': base64,
      'mimeType': VOICE_WAV_MIME_TYPE,
      'durationMs': durationMs,
    };
    return result;
  }

  private buildWavFile(chunks: Uint8Array[], pcmLength: number): Uint8Array {
    const wav = new Uint8Array(VOICE_WAV_HEADER_SIZE + pcmLength);
    const view = new DataView(wav.buffer);
    const byteRate = VOICE_SAMPLE_RATE_HZ * VOICE_CHANNEL_COUNT * VOICE_BYTES_PER_SAMPLE;
    const blockAlign = VOICE_CHANNEL_COUNT * VOICE_BYTES_PER_SAMPLE;
    this.writeAscii(wav, 0, 'RIFF');
    view.setUint32(4, 36 + pcmLength, true);
    this.writeAscii(wav, 8, 'WAVE');
    this.writeAscii(wav, 12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, VOICE_CHANNEL_COUNT, true);
    view.setUint32(24, VOICE_SAMPLE_RATE_HZ, true);
    view.setUint32(28, byteRate, true);
    view.setUint16(32, blockAlign, true);
    view.setUint16(34, VOICE_BITS_PER_SAMPLE, true);
    this.writeAscii(wav, 36, 'data');
    view.setUint32(40, pcmLength, true);
    let offset = VOICE_WAV_HEADER_SIZE;
    for (const chunk of chunks) {
      wav.set(chunk, offset);
      offset += chunk.byteLength;
    }
    return wav;
  }

  private writeAscii(target: Uint8Array, offset: number, text: string) {
    for (let index = 0; index < text.length; index++) {
      target[offset + index] = text.charCodeAt(index);
    }
  }

  private voiceFailure(errorCode: string, errorMessage: string): Record<string, Object> {
    const payload: Record<string, Object> = {
      'success': 0,
      'errorCode': errorCode,
      'errorMessage': errorMessage,
    };
    return payload;
  }

  private playBase64Audio(params: KRAny, callback: KuiklyRenderCallback | null) {
    this.audioPlaybackSequence += 1;
    const sequence = this.audioPlaybackSequence;
    this.releaseAudioPlayer();

    let audioBase64 = '';
    if (typeof params === 'string') {
      try {
        const records = JSON.parse(params) as Record<string, string>;
        const value = records['audioBase64'];
        audioBase64 = typeof value === 'string' ? value : '';
      } catch (error) {
        console.error(`KuiklyRender parse playBase64Audio params failed: ${JSON.stringify(error)}`);
      }
    }
    if (audioBase64.length === 0) {
      callback?.(this.voiceFailure('EMPTY_AUDIO', 'MiMo 没有返回可播放的语音。'));
      return;
    }
    let audioBytes: Uint8Array;
    try {
      audioBytes = new util.Base64Helper().decodeSync(audioBase64);
    } catch (error) {
      console.error(`KuiklyRender decode playback audio failed: ${JSON.stringify(error)}`);
      callback?.(this.voiceFailure('INVALID_AUDIO', 'MiMo 返回的语音数据无效。'));
      return;
    }
    if (audioBytes.byteLength === 0 || audioBytes.byteLength > MAX_PLAYBACK_AUDIO_BYTES) {
      callback?.(this.voiceFailure('INVALID_AUDIO_SIZE', '语音数据为空或超过播放大小限制。'));
      return;
    }
    const context = this.hostAbilityContext();
    if (context == null) {
      callback?.(this.voiceFailure('AUDIO_FILE_FAILED', '语音缓存失败，请稍后重试。'));
      return;
    }
    const filePath = `${context.cacheDir}/stockchat_mimo_tts_${Date.now()}_${sequence}.wav`;
    try {
      const file = fs.openSync(filePath, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
      const payload = audioBytes.buffer.slice(audioBytes.byteOffset, audioBytes.byteOffset + audioBytes.byteLength);
      fs.writeSync(file.fd, payload);
      fs.closeSync(file);
    } catch (error) {
      console.error(`KuiklyRender write playback audio failed: ${JSON.stringify(error)}`);
      this.deletePlaybackFile(filePath);
      callback?.(this.voiceFailure('AUDIO_FILE_FAILED', '语音缓存失败，请稍后重试。'));
      return;
    }
    if (sequence !== this.audioPlaybackSequence) {
      // 写文件期间已被新的播放/停止请求顶掉
      this.deletePlaybackFile(filePath);
      callback?.({ 'success': 1 });
      return;
    }
    this.startAudioPlayback(filePath, sequence, callback);
  }

  private startAudioPlayback(filePath: string, sequence: number, callback: KuiklyRenderCallback | null) {
    media.createAVPlayer().then((player: media.AVPlayer) => {
      if (sequence !== this.audioPlaybackSequence) {
        player.release();
        this.deletePlaybackFile(filePath);
        callback?.({ 'success': 1 });
        return;
      }
      let fd = -1;
      try {
        fd = fs.openSync(filePath, fs.OpenMode.READ_ONLY).fd;
      } catch (error) {
        console.error(`KuiklyRender open playback audio failed: ${JSON.stringify(error)}`);
        player.release();
        this.deletePlaybackFile(filePath);
        callback?.(this.voiceFailure('AUDIO_PLAYBACK_FAILED', '语音播放失败，请稍后重试。'));
        return;
      }
      this.audioPlayer = player;
      this.audioPlaybackFd = fd;
      this.audioPlaybackFile = filePath;
      let playbackStarted = false;
      player.on('stateChange', (state: string) => {
        if (this.audioPlayer !== player) {
          return;
        }
        switch (state) {
          case 'initialized': {
            player.prepare().catch((error: BusinessError) => {
              console.error(`KuiklyRender prepare playback failed: ${error.code} ${error.message}`);
            });
            break;
          }
          case 'prepared': {
            player.play().catch((error: BusinessError) => {
              console.error(`KuiklyRender start playback failed: ${error.code} ${error.message}`);
            });
            break;
          }
          case 'playing': {
            if (!playbackStarted) {
              playbackStarted = true;
              callback?.({ 'success': 1 });
            }
            break;
          }
          case 'completed': {
            this.releaseAudioPlayer();
            break;
          }
          default:
            break;
        }
      });
      player.on('error', (error: BusinessError) => {
        console.error(`KuiklyRender playback error: ${error.code} ${error.message}`);
        if (!playbackStarted) {
          playbackStarted = true;
          callback?.(this.voiceFailure('AUDIO_PLAYBACK_FAILED', '当前设备无法播放 MiMo 语音。'));
        }
        if (this.audioPlayer === player) {
          this.releaseAudioPlayer();
        }
      });
      const source: media.AVFileDescriptor = { fd: fd };
      player.fdSrc = source;
    }).catch((error: BusinessError) => {
      this.deletePlaybackFile(filePath);
      callback?.(this.voiceFailure('AUDIO_PLAYBACK_FAILED', error.message || '语音播放失败，请稍后重试。'));
    });
  }

  private stopAudioPlayback(callback: KuiklyRenderCallback | null) {
    this.audioPlaybackSequence += 1;
    this.releaseAudioPlayer();
    callback?.({ 'success': 1 });
  }

  private releaseAudioPlayer() {
    const player = this.audioPlayer;
    const fd = this.audioPlaybackFd;
    const filePath = this.audioPlaybackFile;
    this.audioPlayer = null;
    this.audioPlaybackFd = -1;
    this.audioPlaybackFile = '';
    if (player == null) {
      this.closePlaybackFd(fd);
      this.deletePlaybackFile(filePath);
      return;
    }
    try {
      player.off('stateChange');
      player.off('error');
    } catch (error) {
      console.error(`KuiklyRender detach player failed: ${JSON.stringify(error)}`);
    }
    // 文件描述符要等播放器真正释放后再关，否则底层可能还在读
    player.release().then(() => {
      this.closePlaybackFd(fd);
      this.deletePlaybackFile(filePath);
    }).catch((error: BusinessError) => {
      console.error(`KuiklyRender release player failed: ${error.code} ${error.message}`);
      this.closePlaybackFd(fd);
      this.deletePlaybackFile(filePath);
    });
  }

  private closePlaybackFd(fd: number) {
    if (fd < 0) {
      return;
    }
    try {
      fs.closeSync(fd);
    } catch (error) {
      console.error(`KuiklyRender close playback fd failed: ${JSON.stringify(error)}`);
    }
  }

  private deletePlaybackFile(filePath: string) {
    if (filePath.length === 0) {
      return;
    }
    fs.unlink(filePath).catch((error: BusinessError) => {
      console.error(`KuiklyRender delete playback file failed: ${error.code} ${error.message}`);
    });
  }

  onDestroy(): void {
    this.chatStreams.forEach((request: StockChatStreamRequest) => request.cancel());
    this.chatStreams.clear();
    this.voiceStartSequence += 1;
    this.voiceStartPending = false;
    this.pendingVoiceStartCallback = null;
    const recording = this.voiceRecording;
    this.voiceRecording = null;
    if (recording != null) {
      recording.capturing = false;
      this.releaseCapturer(recording.capturer);
    }
    this.audioPlaybackSequence += 1;
    this.releaseAudioPlayer();
    super.onDestroy();
  }

  private streamChatCompletion(params: KRAny, callback: KuiklyRenderCallback | null) {
    if (callback == null) {
      return;
    }
    const request = new StockChatStreamRequest(callback, (finished: StockChatStreamRequest) => {
      this.chatStreams.delete(finished);
    });
    this.chatStreams.add(request);
    request.start(params);
  }

  private pickImages(params: KRAny, callback: KuiklyRenderCallback | null) {
    if (this.imagePickerPending) {
      callback?.({
        "success": 0,
        "cancelled": 0,
        "images": [],
        "previewImages": [],
        "truncated": 0,
        "errorCode": "IMAGE_PICKER_BUSY",
        "errorMessage": "图片选择器已打开"
      });
      return;
    }
    let maxCount: number = 1;
    if (typeof params === 'string') {
      try {
        const records = JSON.parse(params) as Record<string, number>;
        const requestedCount = records["maxCount"];
        if (typeof requestedCount === 'number') {
          maxCount = Math.max(1, Math.min(9, Math.trunc(requestedCount)));
        }
      } catch (error) {
        console.error(`KuiklyRender parse image picker params failed: ${JSON.stringify(error)}`);
      }
    }
    const options = new photoAccessHelper.PhotoSelectOptions();
    options.MIMEType = photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE;
    options.maxSelectNumber = maxCount;
    this.imagePickerPending = true;
    const picker = new photoAccessHelper.PhotoViewPicker();
    picker.select(options).then((result: photoAccessHelper.PhotoSelectResult) => {
      const selectedUris = result.photoUris.slice(0, maxCount);
      const truncated = result.photoUris.length > maxCount ? 1 : 0;
      if (selectedUris.length === 0) {
        this.imagePickerPending = false;
        callback?.({
          "success": 1,
          "cancelled": 1,
          "images": [],
          "previewImages": [],
          "truncated": 0
        });
        return;
      }
      // 共享层要求 images 为可直接发给模型的 data URI；预览继续用相册 URI
      this.encodeSelectedImages(selectedUris).then((encoded: Array<string>) => {
        this.imagePickerPending = false;
        const images: Array<string> = [];
        const previewImages: Array<string> = [];
        for (let index = 0; index < encoded.length; index++) {
          if (encoded[index].length > 0) {
            images.push(encoded[index]);
            previewImages.push(selectedUris[index]);
          }
        }
        if (images.length === 0) {
          callback?.({
            "success": 0,
            "cancelled": 0,
            "images": [],
            "previewImages": [],
            "truncated": 0,
            "errorCode": "IMAGE_READ_FAILED",
            "errorMessage": "图片读取失败，请重新选择"
          });
          return;
        }
        callback?.({
          "success": 1,
          "cancelled": 0,
          "images": images,
          "previewImages": previewImages,
          "truncated": truncated
        });
      });
    }).catch((error: BusinessError) => {
      this.imagePickerPending = false;
      callback?.({
        "success": 0,
        "cancelled": 0,
        "images": [],
        "previewImages": [],
        "truncated": 0,
        "errorCode": "IMAGE_PICKER_FAILED",
        "errorMessage": error.message || "图片选择失败，请稍后重试"
      });
    });
  }

  private async encodeSelectedImages(uris: Array<string>): Promise<Array<string>> {
    const encoded: Array<string> = [];
    for (const uri of uris) {
      try {
        encoded.push(await this.encodeImageDataUri(uri));
      } catch (error) {
        console.error(`KuiklyRender encode picked image failed: ${JSON.stringify(error)}`);
        encoded.push('');
      }
    }
    return encoded;
  }

  private async encodeImageDataUri(uri: string): Promise<string> {
    const file = fs.openSync(uri, fs.OpenMode.READ_ONLY);
    try {
      const source = image.createImageSource(file.fd);
      try {
        const info = await source.getImageInfo();
        const width = info.size.width;
        const height = info.size.height;
        const largest = Math.max(width, height);
        const decodingOptions: image.DecodingOptions = { editable: false };
        if (largest > MAX_IMAGE_DIMENSION) {
          const scale = MAX_IMAGE_DIMENSION / largest;
          const desiredSize: image.Size = {
            width: Math.max(1, Math.floor(width * scale)),
            height: Math.max(1, Math.floor(height * scale)),
          };
          decodingOptions.desiredSize = desiredSize;
        }
        const pixelMap = await source.createPixelMap(decodingOptions);
        try {
          const packer = image.createImagePacker();
          try {
            const packingOption: image.PackingOption = { format: 'image/jpeg', quality: IMAGE_JPEG_QUALITY };
            const buffer = await packer.packing(pixelMap, packingOption);
            const base64 = new util.Base64Helper().encodeToStringSync(new Uint8Array(buffer));
            return `data:image/jpeg;base64,${base64}`;
          } finally {
            packer.release();
          }
        } finally {
          pixelMap.release();
        }
      } finally {
        source.release();
      }
    } finally {
      fs.closeSync(file);
    }
  }

  private readAssetFile(params: KRAny, callback: KuiklyRenderCallback) {
    // let startTimeMills = Date.now();
    let records = JSON.parse(params as string) as Record<string, string>;
    let assetPath = records["assetPath"];

    let context = getContext(this) as common.UIAbilityContext;
    /*
    context.resourceManager.getRawFileContent(assetPath, (error: BusinessError, value: Uint8Array) => {
      let textDecoder: util.TextDecoder = util.TextDecoder.create();
      let res: string = textDecoder.decodeWithStream(value);
      callback({
        "result": JSON.parse(res)
      });
    });
    */
    let resDir = context.resourceDir
    let path = resDir + '/' + assetPath
    let assetURI = fileUri.getUriFromPath(path)
    const file = fs.openSync(assetURI, fs.OpenMode.READ_ONLY);
    const stat = fs.statSync(file.fd);
    const arrayBuffer = new ArrayBuffer(stat.size);
    fs.read(file.fd, arrayBuffer, (err: BusinessError, readLen: number) => {
      if (err) {
        console.error("read failed with error message: " + err.message + ", error code: " + err.code);
      } else {
        let buf = new Uint8Array(arrayBuffer, 0, readLen);
        const textDecoder = new util.TextDecoder('utf-8');
        const res = textDecoder.decodeWithStream(new Uint8Array(buf));
        callback({
          'result': JSON.parse(res)
        });
      }
      fs.closeSync(file);
    });
  }
}
