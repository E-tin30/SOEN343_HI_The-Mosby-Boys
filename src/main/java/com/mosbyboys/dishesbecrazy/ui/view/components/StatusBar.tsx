import type { ReactNode } from "react";
import Heading from "./Heading";
import "./StatusBar.css";

export type StatusTone = "sage" | "mustard" | "terracotta" | "tomato" | "navy" | "cocoa";

type StatusBarProps = {
  // Colour of the dot and title.
  tone: StatusTone;
  title: string;
  text: string;
  // The action on the right, e.g. Pause orders or Start Driving.
  children?: ReactNode;
};

// A status dot, heading and one-line explanation, with its action on the right.
// Used for the restaurant's open/paused status and the courier's driving status on their home pages.
export default function StatusBar({ tone, title, text, children }: StatusBarProps) {
  return (
    <div className={`status-bar status-bar--${tone}`}>
      <div className="status-bar__info">
        <span className="status-bar__dot" />
        <div>
          <Heading level={2} className="status-bar__title">{title}</Heading>
          <p className="status-bar__text">{text}</p>
        </div>
      </div>
      {children}
    </div>
  );
}
