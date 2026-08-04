interface PixelCatProps {
  progress?: number;
  state?: "idle" | "detecting" | "downloading" | "complete" | "failed";
  reducedMotion?: boolean;
}

export function PixelCat({ progress = 0, state = "idle", reducedMotion = false }: PixelCatProps) {
  return (
    <div
      className={`pixel-cat pixel-cat--${state} ${reducedMotion ? "pixel-cat--still" : ""}`}
      style={{ "--cat-progress": `${Math.max(2, Math.min(94, progress))}%` } as React.CSSProperties}
      aria-hidden="true"
    >
      <span className="pixel-cat__ear pixel-cat__ear--left" />
      <span className="pixel-cat__ear pixel-cat__ear--right" />
      <span className="pixel-cat__head">
        <i className="pixel-cat__eye pixel-cat__eye--left" />
        <i className="pixel-cat__eye pixel-cat__eye--right" />
        <i className="pixel-cat__nose" />
      </span>
      <span className="pixel-cat__body" />
      <span className="pixel-cat__tail" />
      <span className="pixel-cat__paw pixel-cat__paw--left" />
      <span className="pixel-cat__paw pixel-cat__paw--right" />
    </div>
  );
}
