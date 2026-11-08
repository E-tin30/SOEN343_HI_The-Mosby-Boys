import { Component, type ReactNode } from "react";
import { logError } from "../outputLog";

// Keep the shared output console mounted if a page fails to render.
export default class PageErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() { return { failed: true }; }

  componentDidCatch(error: Error) {
    logError(error, "Could not render this page. Please reload.");
  }

  render() {
    return this.state.failed
      ? <p role="alert">This page could not load. Please reload the application.</p>
      : this.props.children;
  }
}
