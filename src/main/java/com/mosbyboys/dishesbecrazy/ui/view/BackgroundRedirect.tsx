import VideoPage from "./components/VideoPage";

const videoModules = import.meta.glob<string>("./assets/promo-vid2.{mp4,webm,mov}", {
    eager: true,
    import: "default",
    query: "?url",
});
const videoSrc = Object.values(videoModules)[0];

export default function BackgroundRedirect() {
    return (
        <VideoPage
            title="🌮 Taco Tuesday 🌮"
            videoSrc={videoSrc}
            expectedFile="assets/promo-vid2.mp4"
        />
    );
}
