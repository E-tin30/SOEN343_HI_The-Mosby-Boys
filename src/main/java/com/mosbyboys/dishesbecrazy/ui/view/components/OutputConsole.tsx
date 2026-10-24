import { useEffect, useRef, useSyncExternalStore } from "react";
import { getEntries, subscribe } from "../outputLog";
import "./OutputConsole.css";

// Output console (R-DASH-06), shown on every page.
export default function OutputConsole() {
  const entries = useSyncExternalStore(subscribe, getEntries);
  const end = useRef<HTMLLIElement>(null);

  useEffect(() => end.current?.scrollIntoView({ block: "nearest" }), [entries]);

  return (
    <details className="output-console" open>
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
