import React from "react";
import Header from "./components/Header";
import ClusteringPage from "./pages/ClusteringPage";
import Notifications from "./components/Notifications";
import "react-toastify/dist/ReactToastify.min.css";

function App() {
  return (
    <div id={"App"} className="mx-auto max-w-screen-2xl">
      <Header/>
      <main className="lg:pl-72">
        <ClusteringPage/>
      </main>
      <Notifications/>
    </div>
  );
}

export default App;
