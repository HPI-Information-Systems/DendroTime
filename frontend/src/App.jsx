import React from "react";
import Header from "./components/Header";
import ClusteringPage from "./pages/ClusteringPage";

function App() {
  return (
    <div id={"App"} className="mx-auto max-w-screen-2xl">
      <Header/>
      <main className="lg:pl-72">
        <ClusteringPage />
      </main>
    </div>
  );
}

export default App;
