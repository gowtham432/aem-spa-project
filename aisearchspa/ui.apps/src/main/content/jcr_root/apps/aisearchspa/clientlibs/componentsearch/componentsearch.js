document.addEventListener("DOMContentLoaded", function () {

    document.getElementById("searchBtn").addEventListener("click", async () => {

        const query = document.getElementById("searchInput").value;

        if (!query) {
            alert("Please enter a query");
            return;
        }

        const resultsDiv = document.getElementById("results");
        resultsDiv.innerHTML = "Searching...";

        try {
            const response = await fetch("/bin/api/componentSearch", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    query: query,
                    topK: 5
                })
            });

            const data = await response.json();

            resultsDiv.innerHTML = "";

            if (data.results && data.results.length > 0) {
                data.results.forEach(result => {
                    resultsDiv.innerHTML += `
                        <div style="margin-bottom:20px;">
                            <h3>${result.title || "No Title"}</h3>
                            <p><b>Path:</b> ${result.path}</p>
                            <p><b>Score:</b> ${result.score}%</p>
                            <hr/>
                        </div>
                    `;
                });
            } else {
                resultsDiv.innerHTML = "No results found.";
            }

        } catch (error) {
            console.error(error);
            resultsDiv.innerHTML = "Error calling search API.";
        }
    });

});
