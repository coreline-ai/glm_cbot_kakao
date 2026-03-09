import type { ParsedStockQuery, ParsedStockEntity, SummaryRequest, SummaryTimeframe, SummaryCategory } from "../types.js";
import { DOMESTIC_STOCK_ALIASES } from "./alias-dictionary.js";

const MARKET_KEYWORDS = [
  "한국주식시장",
  "국내주식시장",
  "한국증시",
  "국내증시",
  "주식시장",
  "증시",
  "시황",
  "시장정리",
  "시장요약",
  "코스피",
  "코스닥"
];

const SECTOR_KEYWORDS = [
  "반도체",
  "2차전지",
  "바이오",
  "인터넷",
  "게임",
  "금융",
  "방산",
  "자동차",
  "조선",
  "화장품"
];

const STOCK_INTENT_KEYWORDS = [
  "주가",
  "흐름",
  "동향",
  "통향",
  "정리",
  "요약",
  "뉴스",
  "브리핑",
  "분석",
  "전망",
  "어때",
  "어떄",
  "시세",
  "차트",
  "비교",
  "실적",
  "수급",
  "상황"
];

const FILLER_WORDS = new Set([
  "코비서",
  "금일",
  "오늘",
  "지금",
  "최근",
  "흐름",
  "동향",
  "통향",
  "정리",
  "요약",
  "뉴스",
  "브리핑",
  "분석",
  "전망",
  "주가",
  "시세",
  "차트",
  "해줘",
  "알려줘",
  "알려",
  "부탁",
  "부탁해",
  "해석",
  "상황",
  "어때",
  "어떄",
  "비교",
  "와",
  "과",
  "랑",
  "좀"
]);

export function parseStockQuery(request: SummaryRequest): ParsedStockQuery {
  const rawQuestion = request.question?.trim() || request.symbol?.trim() || "";
  const normalizedQuestion = normalizeQuestion(rawQuestion);
  const compactQuestion = normalizedQuestion.replace(/\s+/g, "");
  const timeframe = detectTimeframe(compactQuestion);
  const hasMarketKeyword = MARKET_KEYWORDS.some((keyword) => compactQuestion.includes(keyword));
  const hasSectorKeyword = SECTOR_KEYWORDS.find((keyword) => compactQuestion.includes(keyword));
  const hasStockIntent = STOCK_INTENT_KEYWORDS.some((keyword) => compactQuestion.includes(keyword));
  const entities = detectEntities(normalizedQuestion, compactQuestion, request.symbol, hasMarketKeyword || Boolean(hasSectorKeyword));

  let category: SummaryCategory = "unknown";
  let needsClarification = false;
  let stockSymbol = extractDomesticCode(request.symbol ?? rawQuestion);
  let stockName = undefined as string | undefined;

  if (hasMarketKeyword && entities.every((entity) => entity.type !== "stock")) {
    category = "market_summary";
  } else if (hasSectorKeyword && entities.every((entity) => entity.type !== "stock")) {
    category = "sector_summary";
    entities.unshift({ type: "sector", name: hasSectorKeyword });
  } else if (entities.filter((entity) => entity.type === "stock").length >= 2 || compactQuestion.includes("비교")) {
    category = "stock_compare";
  } else if (entities.some((entity) => entity.type === "stock")) {
    category = "stock_summary";
  } else if (hasStockIntent) {
    category = "unknown";
    needsClarification = true;
  }

  const primaryStock = entities.find((entity) => entity.type === "stock");
  if (primaryStock) {
    stockSymbol = primaryStock.symbol ?? stockSymbol;
    stockName = primaryStock.name;
  }

  return {
    originalQuery: rawQuestion,
    normalizedQuestion,
    category,
    timeframe,
    entities,
    stockSymbol,
    stockName,
    needsClarification,
    wantsNews: compactQuestion.includes("뉴스"),
    wantsTechnical: compactQuestion.includes("차트") || compactQuestion.includes("기술적"),
    debug: {
      hasMarketKeyword,
      hasStockIntent,
      compactQuestion
    }
  };
}

function normalizeQuestion(query: string): string {
  return query
    .replace(/[()[\]{}!?.,/\\|"'`~:;+*-]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function detectEntities(
  normalizedQuestion: string,
  compactQuestion: string,
  symbolInput: string | undefined,
  skipHeuristicStockCandidates: boolean
): ParsedStockEntity[] {
  const entities: ParsedStockEntity[] = [];
  const explicitSymbol = extractDomesticCode(symbolInput ?? normalizedQuestion);
  if (explicitSymbol) {
    entities.push({ type: "stock", name: explicitSymbol, symbol: explicitSymbol });
  }

  for (const entry of DOMESTIC_STOCK_ALIASES) {
    const matchedAlias = entry.aliases.find((alias) => compactQuestion.includes(alias.replace(/\s+/g, "").toLowerCase()));
    if (matchedAlias && !entities.some((entity) => entity.symbol === entry.symbol)) {
      entities.push({
        type: "stock",
        name: entry.name,
        symbol: entry.symbol,
        matchedText: matchedAlias
      });
    }
  }

  if (entities.length > 0) {
    return entities;
  }

  if (skipHeuristicStockCandidates) {
    return entities;
  }

  const candidates = buildCompanyCandidates(normalizedQuestion);
  for (const candidate of candidates) {
    entities.push({
      type: "stock",
      name: candidate,
      matchedText: candidate
    });
  }

  return dedupeEntities(entities);
}

function buildCompanyCandidates(normalizedQuestion: string): string[] {
  const tokens = normalizedQuestion.split(" ").filter(Boolean);
  const filtered = tokens.filter((token) => !FILLER_WORDS.has(token.toLowerCase()));
  if (filtered.length == 0) {
    return [];
  }
  const source = filtered;
  const candidates = new Set<string>();

  for (let size = Math.min(3, source.length); size >= 1; size -= 1) {
    for (let index = 0; index <= source.length - size; index += 1) {
      const phrase = source.slice(index, index + size).join("");
      if (phrase.length < 2) {
        continue;
      }
      if (!/[가-힣a-z]/i.test(phrase)) {
        continue;
      }
      candidates.add(phrase);
    }
  }

  return [...candidates].slice(0, 4);
}

function dedupeEntities(entities: ParsedStockEntity[]): ParsedStockEntity[] {
  const seen = new Set<string>();
  return entities.filter((entity) => {
    const key = `${entity.type}:${entity.symbol ?? entity.name}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function detectTimeframe(compactQuestion: string): SummaryTimeframe | undefined {
  if (compactQuestion.includes("오늘") || compactQuestion.includes("금일")) {
    return "today";
  }
  if (compactQuestion.includes("이번주")) {
    return "1w";
  }
  if (compactQuestion.includes("최근5일") || compactQuestion.includes("5거래일")) {
    return "5d";
  }
  if (compactQuestion.includes("최근한달") || compactQuestion.includes("1개월") || compactQuestion.includes("한달")) {
    return "1m";
  }
  return undefined;
}

function extractDomesticCode(input: string): string | undefined {
  return input.trim().match(/(?<!\d)(\d{6})(?:\.[A-Z]{2,4})?(?!\d)/i)?.[1];
}
