import { useEffect, useState } from "react";

type AvatarContentProps = {
  src?: string | null;
  name: string;
  imageClassName: string;
  fallbackClassName: string;
  fallback?: string;
};

/** Renders the image when available and a stable initial when it is absent or fails to load. */
const AvatarContent = ({ src, name, imageClassName, fallbackClassName, fallback }: AvatarContentProps) => {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const hasImage = Boolean(src && failedSrc !== src);

  useEffect(() => {
    if (src && failedSrc && failedSrc !== src) setFailedSrc(null);
  }, [failedSrc, src]);

  if (hasImage) {
    return <img src={src!} alt="" aria-label={`${name}的头像`} className={imageClassName} onError={() => setFailedSrc(src!)} />;
  }

  return <span className={fallbackClassName}>{fallback ?? (name.trim().charAt(0).toUpperCase() || "知")}</span>;
};

export default AvatarContent;
