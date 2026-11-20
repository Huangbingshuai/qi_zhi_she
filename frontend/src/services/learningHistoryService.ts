export type LearningHistoryItem = {
  id: string;
  title: string;
  summary: string;
  coverImage?: string;
  tags: string[];
  authorNickname: string;
  lastReadAt: number;
};

const HISTORY_LIMIT = 40;

const storageKey = (userId?: number | null) =>
  `qi_zhi_she_learning_history:${userId ?? "guest"}`;
const legacyStorageKey = (userId?: number | null) =>
  `zhiguang_learning_history:${userId ?? "guest"}`;

const read = (userId?: number | null): LearningHistoryItem[] => {
  try {
    const key = storageKey(userId);
    const raw = localStorage.getItem(key) ?? localStorage.getItem(legacyStorageKey(userId));
    if (!raw) return [];
    localStorage.setItem(key, raw);
    localStorage.removeItem(legacyStorageKey(userId));
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((item): item is LearningHistoryItem =>
      !!item && typeof item === "object" &&
      typeof item.id === "string" && typeof item.title === "string" &&
      typeof item.lastReadAt === "number"
    );
  } catch {
    return [];
  }
};

export const learningHistoryService = {
  list: read,

  record(item: Omit<LearningHistoryItem, "lastReadAt">, userId?: number | null) {
    const next: LearningHistoryItem = { ...item, lastReadAt: Date.now() };
    const history = [next, ...read(userId).filter(existing => existing.id !== item.id)]
      .slice(0, HISTORY_LIMIT);
    try {
      localStorage.setItem(storageKey(userId), JSON.stringify(history));
    } catch {
      // Reading history is a convenience and must not block opening content.
    }
    return history;
  },

  clear(userId?: number | null) {
    try {
      localStorage.removeItem(storageKey(userId));
    } catch {
      // Ignore unavailable browser storage.
    }
  }
};
