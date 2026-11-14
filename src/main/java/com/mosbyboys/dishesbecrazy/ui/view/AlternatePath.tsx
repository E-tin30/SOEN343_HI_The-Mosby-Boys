import VideoPage from "./components/VideoPage";

const videoModules = import.meta.glob<string>("./assets/promo-vid.{mp4,webm,mov}", {
    eager: true,
    import: "default",
    query: "?url",
});
const videoSrc = Object.values(videoModules)[0];

export default function AlternatePath() {
    return (
        <VideoPage
            title="☀️ You are my sunshine ☀️"
            videoSrc={videoSrc}
            expectedFile="assets/promo-vid.mp4"
        />
    );
}
