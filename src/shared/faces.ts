import facesJson from './faces.json';

export interface FaceDetails {
  qSid: string;
  qDes: string;
  emCode: string;
  qCid: number;
  aniStickerType: number;
  aniStickerPackId: number;
  aniStickerId: number;
  baseUrl: string;
  advUrl: string;
  emojiNameAlias: string[];
  aniStickerWidth: number;
  aniStickerHeight: number;
}

export const facesMap = new Map<string, FaceDetails>(Object.values(facesJson).map((f) => [f.qSid, f]));
