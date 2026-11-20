import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import AppLayout from "@/components/layout/AppLayout";
import MainHeader from "@/components/layout/MainHeader";
import AuthStatus from "@/features/auth/AuthStatus";
import CourseCard from "@/components/cards/CourseCard";
import { useAuth } from "@/context/AuthContext";
import { learningHistoryService, type LearningHistoryItem } from "@/services/learningHistoryService";
import styles from "./LearningPage.module.css";

const formatReadTime = (timestamp: number) =>
  new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" })
    .format(new Date(timestamp));

const LearningPage = () => {
  const { user, isLoading } = useAuth();
  const [items, setItems] = useState<LearningHistoryItem[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    if (isLoading) return;
    setItems(learningHistoryService.list(user?.id));
    setLoaded(true);
  }, [isLoading, user?.id]);

  const clearHistory = () => {
    learningHistoryService.clear(user?.id);
    setItems([]);
  };

  return (
    <AppLayout
      header={
        <MainHeader
          headline="我的学习"
          subtitle="从最近读过的内容继续，慢慢积累自己的知识路径"
          rightSlot={<AuthStatus />}
        />
      }
    >
      <section className={styles.learningSection} aria-label="最近阅读">
        <div className={styles.sectionHeading}>
          <div>
            <h2 className={styles.sectionTitle}>最近阅读</h2>
            <p className={styles.sectionDescription}>阅读记录保存在当前浏览器中，最多保留 40 条。</p>
          </div>
          {items.length > 0 ? (
            <button type="button" className={styles.clearButton} onClick={clearHistory}>
              清空记录
            </button>
          ) : null}
        </div>

        {items.length > 0 ? (
          <div className={styles.historyGrid}>
            {items.map(item => (
              <div key={item.id} className={styles.historyItem}>
                <CourseCard
                  id={item.id}
                  title={item.title}
                  summary={item.summary}
                  tags={item.tags}
                  teacher={{ name: item.authorNickname }}
                  coverImage={item.coverImage}
                  to={`/post/${item.id}`}
                  layout="horizontal"
                />
                <div className={styles.readTime}>最近阅读：{formatReadTime(item.lastReadAt)}</div>
              </div>
            ))}
          </div>
        ) : (
          <div className={styles.emptyCard}>
            <div className={styles.icon} aria-hidden="true">📚</div>
            <h2 className={styles.title}>{loaded ? "还没有阅读记录" : "正在整理阅读记录…"}</h2>
            <p className={styles.description}>打开一篇感兴趣的知文后，它会出现在这里，方便你接着阅读。</p>
            <Link to="/search" className="ghost-button">去发现内容</Link>
          </div>
        )}
      </section>
    </AppLayout>
  );
};

export default LearningPage;
