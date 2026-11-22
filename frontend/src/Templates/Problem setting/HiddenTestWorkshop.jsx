import Editor from "@monaco-editor/react";
import { CheckCircle2, FlaskConical, Play, Plus, Rocket } from "lucide-react";
import { useState } from "react";
import { useTheme } from "../../Context/themeContext";
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
  const { currentMode } = useTheme();

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
    <section className="hidden-test-workshop" aria-labelledby="hidden-test-workshop-title" aria-busy={busy}>
      <header className="hidden-test-workshop__header">
        <FlaskConical size={24} aria-hidden="true" />
        <div>
          <h3 id="hidden-test-workshop-title">Hidden testcase workshop</h3>
          <p>Run a reference solution and review its output before adding the testcase.</p>
        </div>
      </header>
      <div className="hidden-test-workshop__workspace">
        <section className="hidden-test-workshop__pane" aria-labelledby="workshop-reference-title">
          <div className="hidden-test-workshop__toolbar">
            <h4 id="workshop-reference-title">Reference solution</h4>
            <select aria-label="Reference language" value={language} disabled={busy}
              onChange={(event) => updateField(setLanguage, event.target.value)}>
              <option value="python">Python</option>
              <option value="cpp">C++</option>
              <option value="java">Java</option>
            </select>
          </div>
          <Editor height="320px" language={language} value={sourceCode}
            theme={currentMode === "dark" ? "vs-dark" : "light"}
            onChange={(value) => updateField(setSourceCode, value ?? "")}
            options={{ ariaLabel: "Reference solution", readOnly: busy, minimap: { enabled: false },
              padding: { top: 12 }, scrollBeyondLastLine: false, fontSize: 13 }} />
        </section>
        <div className="hidden-test-workshop__fixtures">
          <section className="hidden-test-workshop__pane">
            <div className="hidden-test-workshop__toolbar">
              <label htmlFor="workshop-hidden-input">Hidden input</label>
              <span>stdin</span>
            </div>
            <textarea id="workshop-hidden-input" rows={5} value={input} maxLength={65536} spellCheck={false}
              disabled={busy} placeholder="Paste the input your solution should receive"
              onChange={(event) => updateField(setInput, event.target.value)} />
          </section>
          <section className="hidden-test-workshop__pane">
            <div className="hidden-test-workshop__toolbar">
              <label htmlFor="workshop-expected-output">Expected output</label>
              {result && <span className="hidden-test-workshop__runtime"><CheckCircle2 size={14} aria-hidden="true" /> {result.executionTimeMs} ms</span>}
            </div>
            <textarea id="workshop-expected-output" rows={5} value={result?.output ?? ""} readOnly
              placeholder={result ? "Empty output" : operation === "generate" ? "Running reference solution…" : "Generate output to preview it here"} />
          </section>
        </div>
      </div>

      <div className="hidden-test-workshop__actions">
        <button type="button" className="problem-editor-save-button"
          disabled={busy || !sourceCode.trim() || sourceCode.length > 65536} onClick={generateOutput}>
          <Play size={16} aria-hidden="true" />
          {operation === "generate" ? "Generating…" : "Generate output"}
        </button>
        <button type="button" className="problem-editor-save-button" disabled={busy || !result} onClick={addTestcase}>
          <Plus size={16} aria-hidden="true" />
          {operation === "save" ? "Adding…" : "Add hidden testcase"}
        </button>
        {sourceCode.length > 65536 && <p role="alert">Reference code is over the 65,536-character limit.</p>}
      </div>
      {feedback && <pre className={feedback.error ? "hidden-test-workshop__feedback is-error" : "hidden-test-workshop__feedback"}
        role={feedback.error ? "alert" : "status"}>{feedback.text}</pre>}
      <footer className="hidden-test-workshop__publish">
        <div>
          <h4>Publish to the problem list</h4>
          <p>Add a public example and a complete hidden testcase bundle before publishing.</p>
        </div>
        <button type="button" className="problem-editor-save-button" disabled={busy || !hasStoredTests || !!result}
          onClick={publish}>
          <Rocket size={16} aria-hidden="true" />
          {operation === "publish" ? "Publishing…" : "Publish problem"}
        </button>
      </footer>
    </section>
  );
}
