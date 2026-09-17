import { ToastProvider } from "./components/Toast/ToastProvider";
import CommunityWorkspace from "./components/Community/CommunityWorkspace";

export default function App() {
  return (
    <ToastProvider>
      <CommunityWorkspace />
    </ToastProvider>
  );
}