import { useEffect, useRef, useSyncExternalStore } from "react";
import { getEntries, subscribe } from "../outputLog";
import "./OutputConsole.css";

// Output console (R-DASH-06), shown on every page.
export default function OutputConsole() {
  const entries = useSyncExternalStore(subscribe, getEntries);
  const end = useRef<HTMLLIElement>(null);
  const root = useRef<HTMLDetailsElement>(null);

  // Publish the console's real height so pages can pad exactly that much.
  useEffect(() => {
    const el = root.current;
    if (!el) return;
    const style = document.documentElement.style;
    const observer = new ResizeObserver(() => {
      style.setProperty("--console-height", `${el.getBoundingClientRect().height}px`);
    });
    observer.observe(el);
    return () => {
      observer.disconnect();
      style.removeProperty("--console-height");
    };
  }, []);

  useEffect(() => {
    end.current?.scrollIntoView({ block: "nearest" });
  }, [entries]);

  return (
    <details ref={root} className="output-console" open>
      <summary>Output console ({entries.length})</summary>
      <ol>
        {entries.map((e, i) => (
          <li key={i} className={`output-console__${e.kind}`}>
            [{e.time}] [{e.kind}] {e.text}
          </li>
        ))}
        <li ref={end} />
      </ol>
    </details>
  );
}
