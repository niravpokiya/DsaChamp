import { useState } from "react";
import { add_workshop_testcase, publish_workshop_problem, run_workshop_reference } from "../../utils/problem-apis";

function errorMessage(error, fallback) {
  return error.response?.data?.detail || error.response?.data?.message || fallback;
}

export default function HiddenTestWorkshop({ problemId, hasStoredTests, onTestcaseAdded, onPublished }) {
  const [language, setLanguage] = useState("python");
  const [sourceCode, setSourceCode] = useState("");
  const [input, setInput] = useState("");
  const [result, setResult] = useState(null);
  const [operation, setOperation] = useState(null);
  const [feedback, setFeedback] = useState(null);
  const busy = operation !== null;

  function updateField(setValue, value) {
    setValue(value);
    setResult(null);
    setFeedback(null);
  }

  async function generateOutput() {
    setOperation("generate");
    setResult(null);
    setFeedback(null);
    try {
      const { data } = await run_workshop_reference(problemId, { language, sourceCode, input });
      if (data.status === "SUCCESS" && typeof data.output === "string") {
        setResult(data);
      } else {
        setFeedback({ error: true, text: data.error || data.status || "Reference solution failed." });
      }
    } catch (error) {
      setFeedback({ error: true, text: errorMessage(error, "Could not run the reference solution.") });
    } finally {
      setOperation(null);
    }
  }

  async function addTestcase() {
    setOperation("save");
    setFeedback(null);
    try {
      const { data } = await add_workshop_testcase(problemId, { input, output: result.output });
      setResult(null);
      setInput("");
      setFeedback({ text: data.added ? "Hidden testcase added." : "This hidden testcase already exists." });
      onTestcaseAdded();
    } catch (error) {
      setFeedback({ error: true, text: errorMessage(error, "Could not save the hidden testcase.") });
    } finally {
      setOperation(null);
    }
  }

  async function publish() {
    setOperation("publish");
    setFeedback(null);
    try {
      await publish_workshop_problem(problemId);
      onPublished();
    } catch (error) {
      setFeedback({ error: true, text: errorMessage(error, "Could not publish. Check the saved statement, examples, and testcase pairs.") });
    } finally {
      setOperation(null);
    }
  }

  return (
    <section className="hidden-test-workshop" aria-labelledby="hidden-test-workshop-title">
      <h3 id="hidden-test-workshop-title">Generate a hidden testcase</h3>
      <p>Run a reference solution on your input, review the output, then add the pair to your tests.</p>
      <label>
        Reference language
        <select value={language} disabled={busy} onChange={(event) => updateField(setLanguage, event.target.value)}>
          <option value="python">Python</option>
          <option value="cpp">C++</option>
          <option value="java">Java</option>
        </select>
      </label>
      <label>
        Reference solution
        <textarea rows={8} value={sourceCode} maxLength={65536} spellCheck={false} disabled={busy}
          onChange={(event) => updateField(setSourceCode, event.target.value)} />
      </label>
      <label>
        Hidden input
        <textarea rows={4} value={input} maxLength={65536} spellCheck={false} disabled={busy}
          onChange={(event) => updateField(setInput, event.target.value)} />
      </label>
      <button type="button" className="problem-editor-save-button" disabled={busy || !sourceCode.trim()}
        onClick={generateOutput}>
        {operation === "generate" ? "Generating…" : "Generate output"}
      </button>
      {result && (
        <>
          <label>
            Expected output · {result.executionTimeMs} ms
            <textarea rows={4} value={result.output} readOnly />
          </label>
          <button type="button" className="problem-editor-save-button" disabled={busy} onClick={addTestcase}>
            {operation === "save" ? "Adding…" : "Add hidden testcase"}
          </button>
        </>
      )}
      {feedback && <pre role={feedback.error ? "alert" : "status"}>{feedback.text}</pre>}
      <div className="hidden-test-workshop__publish">
        <p>Publishing makes the saved problem visible to players. Add a public example and a complete hidden testcase bundle first.</p>
        <button type="button" className="problem-editor-save-button" disabled={busy || !hasStoredTests || !!result}
          onClick={publish}>
          {operation === "publish" ? "Publishing…" : "Publish problem"}
        </button>
      </div>
    </section>
  );
}
