import { NestFactory } from '@nestjs/core';
import { Module, Controller, Get, Post, Body, Injectable } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import axios from 'axios';
import * as path from 'path';

/**
 * [Service] ChatService
 * GLM-4 AI 엔진과의 통신 및 메시지 분석 로직을 담당합니다.
 */
@Injectable()
class ChatService {
  private readonly WAKE_WORD = '코비서';

  constructor(private configService: ConfigService) { }

  // GLM-4 API 호출을 위한 저수준 메서드
  private async callGlmApi(messages: any[], temperature?: number): Promise<string | null> {
    console.log('\n┌── [AI API Request] ──────────────────────────');
    console.log(`│ Model: ${this.configService.get<string>('GLM_MODEL') || 'GLM-4-Plus'}`);
    console.log(`│ Messages: ${JSON.stringify(messages, null, 2).split('\n').join('\n│ ')}`);
    console.log('└──────────────────────────────────────────────');

    try {
      const apiKey = this.configService.get<string>('GLM_API_KEY');
      const baseURL = this.configService.get<string>('GLM_BASE_URL') || 'https://api.z.ai/api/coding/paas/v4';
      const modelName = this.configService.get<string>('GLM_MODEL') || 'GLM-4-Plus';

      const response = await axios.post(
        `${baseURL}/chat/completions`,
        {
          model: modelName,
          messages,
          temperature: temperature ?? 0.2,
        },
        {
          headers: {
            'Authorization': `Bearer ${apiKey}`,
            'Content-Type': 'application/json',
          },
        },
      );

      const reply = response.data.choices[0]?.message?.content || null;
      console.log('┌── [AI API Response] ─────────────────────────');
      console.log(`│ Content: ${reply?.split('\n').join('\n│ ')}`);
      console.log('└──────────────────────────────────────────────\n');
      return reply;
    } catch (error: any) {
      console.error('│ [Error]: GLM API Error:', error.response?.data || error.message);
      console.log('└──────────────────────────────────────────────\n');
      return null;
    }
  }

  // 메시지 입력을 분석하여 호출어 기반 응답 생성
  async processMessage(message: string) {
    if (message.trim().startsWith(this.WAKE_WORD)) {
      const query = message.trim().slice(this.WAKE_WORD.length).trim();
      console.log(`\n🔍 [Analysis]: 호출어 감지됨 -> 쿼리: "${query}"`);
      if (!query) return null;

      const aiResponse = await this.generateChatResponse(query);
      return aiResponse ? { summary: aiResponse } : null;
    }
    console.log(`\n💤 [Analysis]: 호출어 없음 -> 무시됨: "${message.slice(0, 20)}..."`);
    return null;
  }

  // AI 응답 생성을 위한 프롬프트 구성
  private async generateChatResponse(query: string): Promise<string | null> {
    const temperature = parseFloat(this.configService.get<string>('GLM_TEMPERATURE') || '0.2');
    const prompt = `
      너는 '코비서'라는 이름의 친절한 AI 비서야.
      한국어로 자연스럽고 간결하게(3문장 이내) 답변해줘.
      사용자 질문: "${query}"
    `;
    return await this.callGlmApi([{ role: 'user', content: prompt }], temperature);
  }
}

/**
 * [Controller] AppController
 * HTTP 요청 엔드포인트를 정의합니다.
 */
@Controller()
class AppController {
  constructor(private readonly chatService: ChatService) { }

  // 서버 상태 확인
  @Get()
  healthCheck() {
    return { status: 'ok', message: '코비서 AI 서버 작동 중 (Single File Mode)' };
  }

  // 메시지 처리 API
  @Post('chat/process')
  async process(@Body() body: { message: string }) {
    console.log(`[Message]: ${body.message}`);
    const result = await this.chatService.processMessage(body.message);
    return result || { summary: null };
  }

  // 히스토리 조회 (Minimal 버전)
  @Get('chat/history')
  getHistory() {
    return [];
  }
}

/**
 * [Module] AppModule
 * 앱의 모든 구성을 통합하는 루트 모듈입니다.
 */
@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: path.resolve(__dirname, '../.env'),
    }),
  ],
  controllers: [AppController],
  providers: [ChatService],
})
class AppModule { }

/**
 * [Bootstrap] 애플리케이션 시작점
 */
async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  // 기본 미들웨어 설정
  app.enableCors({ origin: true, credentials: true });

  const port = process.env.PORT ?? 3001;
  await app.listen(port, '0.0.0.0');

  console.log(`\n✨ [코비서] 서버가 단일 파일 모드로 시작되었습니다: http://localhost:${port}\n`);
}

bootstrap();
