export interface SectorPreset {
  name: string;
  aliases: string[];
  symbols: Array<{
    symbol: string;
    name: string;
  }>;
}

export const SECTOR_PRESETS: SectorPreset[] = [
  {
    name: "반도체",
    aliases: ["반도체", "반도체주", "chip", "chips"],
    symbols: [
      { symbol: "005930", name: "삼성전자" },
      { symbol: "000660", name: "SK하이닉스" },
      { symbol: "042700", name: "한미반도체" }
    ]
  },
  {
    name: "2차전지",
    aliases: ["2차전지", "이차전지", "배터리", "배터리주"],
    symbols: [
      { symbol: "373220", name: "LG에너지솔루션" },
      { symbol: "006400", name: "삼성SDI" },
      { symbol: "066970", name: "엘앤에프" }
    ]
  },
  {
    name: "인터넷",
    aliases: ["인터넷", "플랫폼", "인터넷주"],
    symbols: [
      { symbol: "035420", name: "NAVER" },
      { symbol: "035720", name: "카카오" },
      { symbol: "323410", name: "카카오뱅크" }
    ]
  },
  {
    name: "자동차",
    aliases: ["자동차", "자동차주", "완성차"],
    symbols: [
      { symbol: "005380", name: "현대차" },
      { symbol: "000270", name: "기아" },
      { symbol: "012330", name: "현대모비스" }
    ]
  },
  {
    name: "바이오",
    aliases: ["바이오", "바이오주", "제약바이오"],
    symbols: [
      { symbol: "207940", name: "삼성바이오로직스" },
      { symbol: "068270", name: "셀트리온" },
      { symbol: "326030", name: "SK바이오팜" }
    ]
  }
];

export function findSectorPreset(query: string): SectorPreset | undefined {
  const normalized = query.replace(/\s+/g, "").toLowerCase();
  return SECTOR_PRESETS.find((sector) =>
    sector.aliases.some((alias) => normalized.includes(alias.replace(/\s+/g, "").toLowerCase()))
  );
}

