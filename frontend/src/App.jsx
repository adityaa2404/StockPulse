import { useEffect, useMemo, useState } from 'react';

const API = import.meta.env.VITE_API_URL || 'http://localhost:8080';

function App() {
  const [products, setProducts] = useState([]);
  const [pricing, setPricing] = useState([]);
  const [reorders, setReorders] = useState([]);
  const [status, setStatus] = useState('ALL');
  const [category, setCategory] = useState('ALL');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    setBusy(true); setError('');
    try {
      const [p, ps, rs] = await Promise.all([
        fetch(API + '/products').then(r => r.json()),
        fetch(API + '/pricing-suggestions?status=PENDING').then(r => r.json()),
        fetch(API + '/reorder-suggestions?status=PENDING').then(r => r.json())
      ]);
      setProducts(p); setPricing(ps); setReorders(rs);
    } catch (e) { setError('Backend unavailable. Start Spring Boot on port 8080.'); }
    finally { setBusy(false); }
  };

  useEffect(() => { load(); }, []);

  const filtered = useMemo(() => products.filter(p =>
    (status === 'ALL' || p.status === status) &&
    (category === 'ALL' || p.category === category)
  ), [products, status, category]);

  const pending = [
    ...pricing.map(s => ({...s, kind:'PRICE'})),
    ...reorders.map(s => ({...s, kind:'REORDER'}))
  ];

  const accept = async (item) => {
    const url = API + (item.kind === 'PRICE' ? '/pricing-suggestions/' : '/reorder-suggestions/') + item.id;
    await fetch(url, { method:'PATCH', headers:{'Content-Type':'application/json'}, body:JSON.stringify({status:'ACCEPTED'}) });
    load();
  };
  const reject = async (item) => {
    const url = API + (item.kind === 'PRICE' ? '/pricing-suggestions/' : '/reorder-suggestions/') + item.id;
    await fetch(url, { method:'PATCH', headers:{'Content-Type':'application/json'}, body:JSON.stringify({status:'REJECTED'}) });
    load();
  };
  const simulateSale = async (p) => {
    if (p.stockLevel <= 0) return;
    await fetch(API + '/products/' + p.id + '/orders', {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({quantity:1})
    });
    load();
  };

  const low = products.filter(p => p.stockLevel <= p.reorderThreshold).length;

  return <div className="app">
    <header><div><strong>StockPulse</strong><span>AI Inventory & Dynamic Pricing Engine</span></div>
      <div className="header-actions"><span className="online">● Online</span><button onClick={load}>{busy ? 'Refreshing...' : 'Refresh'}</button></div>
    </header>
    <main>
      {error && <div className="error">{error}</div>}
      <section className="metrics">
        <Metric label="Total Products" value={products.length}/>
        <Metric label="Pending Reviews" value={pending.length}/>
        <Metric label="Low Stock" value={low}/>
      </section>

      <section className="panel">
        <div className="section-head"><div><h2>Inventory Catalog</h2><p>Real-time stock, pricing and demand signals</p></div>
          <div className="filters">
            <label>Status <select value={status} onChange={e=>setStatus(e.target.value)}>
              <option>ALL</option><option>ACTIVE</option><option>PRICE_REVIEW_PENDING</option><option>OUT_OF_STOCK</option>
            </select></label>
            <label>Category <select value={category} onChange={e=>setCategory(e.target.value)}>
              <option>ALL</option><option>ELECTRONICS</option><option>APPAREL</option><option>HOME</option>
            </select></label>
          </div>
        </div>
        <div className="table-wrap"><table><thead><tr>
          <th>SKU</th><th>Product</th><th>Category</th><th>Price</th><th>Stock</th><th>Threshold</th><th>Velocity</th><th>Status</th><th>Action</th>
        </tr></thead><tbody>{filtered.map(p=><tr key={p.id}>
          <td className="mono">{p.sku}</td><td><b>{p.name}</b></td><td>{p.category}</td>
          <td className="mono">₹{Number(p.currentPrice).toFixed(2)}</td>
          <td className={p.stockLevel <= p.reorderThreshold ? 'low mono':'mono'}>{p.stockLevel}</td>
          <td className="mono">{p.reorderThreshold}</td><td className="mono">{p.demandVelocity}</td>
          <td><Badge value={p.status}/></td>
          <td><button className="small" onClick={()=>simulateSale(p)} disabled={p.stockLevel===0}>Simulate Sale</button></td>
        </tr>)}</tbody></table></div>
      </section>

      <section><div className="section-title"><h2>Pending Recommendations</h2><p>Automated pricing and replenishment suggestions awaiting approval</p></div>
        <div className="cards">{pending.length ? pending.map(x=><Recommendation key={x.kind+x.id} item={x} onAccept={accept} onReject={reject}/>) :
          <div className="empty">No pending recommendations.</div>}</div>
      </section>
    </main>
    <footer>StockPulse Inventory Operations Management</footer>
  </div>
}

function Metric({label,value}) { return <div className="metric"><small>{label}</small><strong>{value}</strong></div> }
function Badge({value}) { return <span className={'badge '+value.toLowerCase()}>{value}</span> }
function Recommendation({item,onAccept,onReject}) {
  const p=item.product || {};
  const price=item.kind==='PRICE';
  return <article className="card">
    <div className="card-top"><b>{price?'Pricing Recommendation':'Reorder Recommendation'}</b><span>{Math.round((item.confidence||0)*100)}% confidence</span></div>
    <h3>{p.name || 'Product'}</h3><code>{p.sku || ''}</code>
    <div className="recommend">{price ? <>Current: ₹{Number(item.currentPrice).toFixed(2)}<br/><b>Recommended: ₹{Number(item.recommendedPrice).toFixed(2)} ({item.direction})</b></> :
      <>Current stock: {item.currentStock}<br/><b>Recommended quantity: +{item.recommendedQuantity}</b></>}</div>
    <p>{item.reasoning}</p><div className="trigger">{item.triggerReason}</div>
    <div className="buttons"><button onClick={()=>onReject(item)}>Reject</button><button className="accept" onClick={()=>onAccept(item)}>Accept</button></div>
  </article>
}
export default App;