type SkeletonProps = {
  rows?: number;
};

export function Skeleton({ rows = 3 }: SkeletonProps) {
  return (
    <div className="skeleton-list" aria-label="Cargando">
      {Array.from({ length: rows }, (_, index) => (
        <div className="skeleton-card" key={index}>
          <span />
          <strong />
          <p />
          <p />
        </div>
      ))}
    </div>
  );
}
