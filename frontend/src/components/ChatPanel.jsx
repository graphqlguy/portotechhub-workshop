import { useEffect, useRef, useState } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { useGraphQL } from '../context/GraphQLContext'

// The clinic assistant from chapter 5. It sends the question to /api/chat, where the
// model answers it with the operation files as tools.
export default function ChatPanel() {
  const { chat } = useGraphQL()
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState([])
  const [question, setQuestion] = useState('')
  const [waiting, setWaiting] = useState(false)
  const list = useRef(null)

  // Keeps the latest message in view
  useEffect(() => {
    if (list.current) {
      list.current.scrollTop = list.current.scrollHeight
    }
  }, [messages, waiting])

  if (!chat) {
    return null
  }

  async function ask(event) {
    event.preventDefault()
    const text = question.trim()
    if (!text || waiting) {
      return
    }
    setQuestion('')
    setMessages((previous) => [...previous, { from: 'you', text }])
    setWaiting(true)
    try {
      const response = await fetch('/api/chat', { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: text })
      const answer = await response.text()
      setMessages((previous) => [
        ...previous,
        { from: 'assistant', text: response.ok ? answer : `HTTP ${response.status}: ${answer}` },
      ])
    } catch (error) {
      setMessages((previous) => [...previous, { from: 'assistant', text: `The backend did not answer: ${error.message}` }])
    } finally {
      setWaiting(false)
    }
  }

  return (
    <div className="fixed right-4 bottom-4 z-10 flex flex-col items-end">
      {open && (
        <div className="mb-3 flex h-[28rem] w-80 flex-col rounded border border-spring-grey bg-white shadow-lg sm:w-96">
          <div className="bg-spring-brown px-4 py-2 text-sm text-spring-light-grey">Clinic assistant</div>
          <div ref={list} className="flex-1 space-y-3 overflow-y-auto p-3 text-sm">
            {messages.length === 0 && (
              <p className="text-spring-grey">Ask about owners, pets, vets and visits, or ask it to book a visit.</p>
            )}
            {messages.map((message, i) =>
              message.from === 'you' ? (
                <p key={i} className="ml-8 whitespace-pre-wrap rounded bg-spring-light-grey px-3 py-2">
                  {message.text}
                </p>
              ) : (
                // Models answer in Markdown. react-markdown renders it without running any HTML it contains.
                <div key={i} className="chat-answer mr-8 rounded bg-spring-green/15 px-3 py-2">
                  <Markdown remarkPlugins={[remarkGfm]}>{message.text}</Markdown>
                </div>
              ),
            )}
            {waiting && <p className="text-spring-grey">Thinking…</p>}
          </div>
          <form onSubmit={ask} className="flex border-t border-spring-grey">
            <input
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Which visits are booked next week?"
              className="flex-1 px-3 py-2 text-sm focus:outline-none"
            />
            <button className="px-4 text-sm text-spring-dark-green" disabled={waiting}>
              Send
            </button>
          </form>
        </div>
      )}
      <button
        onClick={() => setOpen(!open)}
        className="rounded-full bg-spring-green px-5 py-3 text-sm text-white shadow-lg hover:bg-spring-dark-green"
      >
        {open ? 'Close' : 'Ask the assistant'}
      </button>
    </div>
  )
}
