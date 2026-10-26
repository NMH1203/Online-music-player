package vn.edu.usth.online_music_player.ui.profile;

import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.time.*;
import java.time.format.DateTimeFormatter;
import vn.edu.usth.online_music_player.databinding.FragmentProfileBinding;
import vn.edu.usth.online_music_player.data.account.AccountModels.*;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private ProfileViewModel model;
    private ProfileViewModel.State current;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle saved) {
        binding=FragmentProfileBinding.inflate(inflater,parent,false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved) {
        model=new ViewModelProvider(requireActivity()).get(ProfileViewModel.class);
        model.state().observe(getViewLifecycleOwner(),this::render);
        binding.loginButton.setOnClickListener(v->login(false));
        binding.registerButton.setOnClickListener(v->login(true));
        binding.profileRetry.setOnClickListener(v->model.refresh());
        binding.editProfileButton.setOnClickListener(v->editProfile());
        binding.upgradeButton.setOnClickListener(v->paywall());
        binding.resetPlanButton.setOnClickListener(v->new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chuyển về Free?").setMessage("Quảng cáo và giới hạn Free sẽ được áp dụng lại. Playlist hiện có vẫn được giữ.")
            .setNegativeButton("Hủy",null).setPositiveButton("Reset demo",(d,w)->model.reset()).show());
        binding.logoutButton.setOnClickListener(v->model.logout());
        binding.historyButton.setOnClickListener(v->model.loadHistory());
    }
    @Override public void onResume() { super.onResume(); if(model!=null) model.refresh(); }
    private void login(boolean register) {
        String email=binding.loginEmail.getText().toString().trim(), password=binding.loginPassword.getText().toString(),
            name=binding.loginName.getText().toString().trim();
        if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { binding.loginEmail.setError("Nhập email hợp lệ"); return; }
        if(password.isEmpty() || (register && password.length()<8)) { binding.loginPassword.setError("Mật khẩu đăng ký cần ít nhất 8 ký tự"); return; }
        if(register && name.isEmpty()) { binding.loginName.setError("Nhập tên hiển thị"); return; }
        model.login(email,password,name,register);
    }
    private void render(ProfileViewModel.State state) {
        if(binding==null) return; current=state;
        binding.profileProgress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        binding.profileMessage.setText(state.message);
        binding.loginPanel.setVisibility(state.signedIn ? View.GONE : View.VISIBLE);
        binding.accountPanel.setVisibility(state.signedIn ? View.VISIBLE : View.GONE);
        enable(binding.getRoot(),!state.busy);
        if(!state.signedIn || state.profile==null || state.entitlement==null) return;
        binding.loginPassword.setText("");
        Profile p=state.profile; Entitlement e=state.entitlement;
        binding.profileName.setText(p.displayName); binding.profileEmail.setText(p.email);
        binding.planName.setText(e.planName);
        binding.planDates.setText("Bắt đầu: "+date(e.startsAt)+(e.expiresAt==null ? "\nKhông có ngày hết hạn" : "\nHết hạn: "+date(e.expiresAt)));
        binding.planBenefits.setText((e.adFree ? "Không quảng cáo" : "Quảng cáo mở app và giữa hai bài")
            +"\n"+(e.maxPlaylists==null ? "Playlist không giới hạn" : "Tối đa "+e.maxPlaylists+" playlist")
            +"\n"+(e.historyDays==null ? "Toàn bộ lịch sử nghe" : "Lịch sử nghe "+e.historyDays+" ngày"));
        binding.upgradeButton.setVisibility("PREMIUM".equals(e.planCode) ? View.GONE : View.VISIBLE);
        binding.upgradeButton.setEnabled(!state.busy && e.demoEnabled);
        binding.demoNote.setText(e.demoEnabled ? "Thanh toán mô phỏng • Không thu tiền thật" : "Máy chủ chưa bật chế độ nâng cấp mô phỏng.");
        binding.resetPlanButton.setVisibility(e.demoEnabled && "PREMIUM".equals(e.planCode) ? View.VISIBLE : View.GONE);
        StringBuilder history=new StringBuilder();
        for(History h:state.history) history.append(h.title).append(" — ").append(h.artist_name).append("\n").append(date(h.started_at)).append("\n\n");
        binding.historyEntries.setText(history.toString());
    }
    private void enable(View view,boolean enabled) {
        view.setEnabled(enabled);
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) enable(((ViewGroup)view).getChildAt(i),enabled);
    }
    private void paywall() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Premium • 30 ngày")
            .setMessage("Không quảng cáo\nPlaylist không giới hạn\nToàn bộ lịch sử nghe\n\nĐây là thanh toán mô phỏng, giá 0 ₫. Không nhập thông tin thẻ.")
            .setPositiveButton("Kích hoạt mô phỏng",(d,w)->model.upgrade("success"))
            .setNeutralButton("Mô phỏng thất bại",(d,w)->model.upgrade("failed"))
            .setNegativeButton("Để sau",null).show();
    }
    private void editProfile() {
        if(current==null || current.profile==null) return;
        LinearLayout form=new LinearLayout(requireContext()); form.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(24*getResources().getDisplayMetrics().density); form.setPadding(pad,0,pad,0);
        EditText name=new EditText(requireContext()); name.setHint("Tên hiển thị"); name.setSingleLine(true); name.setText(current.profile.displayName);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        EditText bio=new EditText(requireContext()); bio.setHint("Giới thiệu"); bio.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bio.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)}); bio.setText(current.profile.bio);
        form.addView(name); form.addView(bio);
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle("Chỉnh sửa hồ sơ")
            .setView(form).setNegativeButton("Hủy",null).setPositiveButton("Lưu",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(button-> {
            String value=name.getText().toString().trim();
            if(value.isEmpty()) { name.setError("Tên không được để trống"); return; }
            model.saveName(value,bio.getText().toString()); dialog.dismiss();
        })); dialog.show();
    }
    private String date(String value) {
        if(value==null) return "—";
        try { return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value)); }
        catch(Exception e) { return value; }
    }
    @Override public void onDestroyView() { binding=null; super.onDestroyView(); }
}
