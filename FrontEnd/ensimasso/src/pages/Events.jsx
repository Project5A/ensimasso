import React from "react";
import { Timeline } from "../components/Event/Timeline.tsx";
import './style/Events.css';

function Events() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-purple-50 to-pink-50 py-16 px-4">
      <Timeline />
    </div>
  );
}
  
export default Events;

