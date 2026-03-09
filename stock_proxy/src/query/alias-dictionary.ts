export interface StockAliasEntry {
  symbol: string;
  name: string;
  aliases: string[];
}

export const DOMESTIC_STOCK_ALIASES: StockAliasEntry[] = [
  { symbol: "005930", name: "삼성전자", aliases: ["삼성전자", "삼전"] },
  { symbol: "000660", name: "SK하이닉스", aliases: ["sk하이닉스", "하이닉스", "하닉"] },
  { symbol: "035420", name: "NAVER", aliases: ["naver", "네이버"] },
  { symbol: "035720", name: "카카오", aliases: ["카카오"] },
  { symbol: "068270", name: "셀트리온", aliases: ["셀트리온"] },
  { symbol: "105560", name: "KB금융", aliases: ["kb금융", "국민은행지주"] },
  { symbol: "055550", name: "신한지주", aliases: ["신한지주"] },
  { symbol: "012330", name: "현대모비스", aliases: ["현대모비스"] },
  { symbol: "005380", name: "현대차", aliases: ["현대차", "현대자동차"] },
  { symbol: "012450", name: "한화에어로스페이스", aliases: ["한화에어로", "한에어", "한화에어로스페이스"] },
  { symbol: "034020", name: "두산에너빌리티", aliases: ["두산에너빌리티", "두빌"] },
  { symbol: "207940", name: "삼성바이오로직스", aliases: ["삼성바이오로직스", "삼바"] },
  { symbol: "006400", name: "삼성SDI", aliases: ["삼성sdi", "sdi"] },
  { symbol: "066570", name: "LG전자", aliases: ["lg전자"] },
  { symbol: "051910", name: "LG화학", aliases: ["lg화학"] },
  { symbol: "003670", name: "포스코홀딩스", aliases: ["포스코홀딩스", "포홀", "posco홀딩스"] },
  { symbol: "323410", name: "카카오뱅크", aliases: ["카카오뱅크", "카뱅"] },
  { symbol: "373220", name: "LG에너지솔루션", aliases: ["lg에너지솔루션", "엘지엔솔", "엔솔"] }
];

